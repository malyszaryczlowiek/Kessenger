package util

import akka.actor.{ActorRef, PoisonPill}
import components.db.DbExecutor
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.{KafkaConfigurator, KafkaProductionConfigurator}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration, Invitation, Message, PartitionOffset, UserOffsetUpdate, Writing}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import play.api.db.Database

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Try, Using}

class BrokerExecutor( private val out: ActorRef, private val db: Database, private val env: KafkaConfigurator, private val ec: ExecutionContext) { // (implicit s: String)

  private val ka: KessengerAdmin = new KessengerAdmin(env)

  private var conf: Option[Configuration] = None

  private var selfReference: Option[ActorRef] = None

  private val continueReading: AtomicBoolean = new AtomicBoolean(true)

  private val messageProducer: KafkaProducer[String, Message] = ka.createMessageProducer

  private val writingProducer: KafkaProducer[String, Writing] = ka.createWritingProducer

  private val newChats:        collection.concurrent.TrieMap[ChatId, List[PartitionOffset]] = collection.concurrent.TrieMap.empty
  private val chats:           collection.concurrent.TrieMap[ChatId, List[PartitionOffset]] = collection.concurrent.TrieMap.empty

  private var listener: Option[Future[Any]] = None

  /*
  TODO
    1. napisać mechanizm zamykania actora podobny do tego dla consumerów ale dla PRODUCENTÓW ??? Czy aby warto ???
    2. napisać mechanizm przypisywania czatów consumerom (wraz z uwzględnianiem tego aby zczytał ostatnie 20 wiadomości)
    3. następnie napisać mechanizm odczytywania wcześniejszych wiadomości
       i powrotu do ostatnio czytanych offsetów tak aby z powrotem móc zczytywać bierzące wiadomości.
    4. Napisać we front-endzie mechanizm stopowania prób uruchomienia reconnectWSTimer





  TODO
   zmienić tak aby pobierał po 10 ostatnich wiadomości

   */

  def initialize(conf: Configuration): Unit = {
    this.conf = Option(conf)
    this.conf match {
      case Some( c ) =>
        println(s"inicjalizacja konfiguracji powiodła się.")

        this.listener = Option(
          Future {

            // at the beginning we create kafka consumer to consume invitation
            // from our joining topic.
            println(s"Jestem w future w BrokerExecutor.")
            Using( this.ka.createInvitationConsumer(conf.me.userId.toString) ) {
              (invitationConsumer: KafkaConsumer[String, Invitation]) => {
                println(s"utworzyłem Invitation consumer")
                Using(this.ka.createMessageConsumer(conf.me.userId.toString)) {
                  (messageConsumer: KafkaConsumer[String, Message]) => {
                    println(s"utworzyłem Message consumer")
                    Using(this.ka.createWritingConsumer(conf.me.userId.toString)) {
                      (writingConsumer: KafkaConsumer[String, Writing]) => {
                        println(s"utworzyłem Writing consumer")
                        // we set topic to read from (this topic has only one partition)
                        val myJoinTopic = new TopicPartition(Domain.generateJoinId(conf.me.userId), 0)

                        // assign this toopic to kafka consumer
                        invitationConsumer.assign(java.util.List.of(myJoinTopic))

                        // and assign offset for that topic partition
                        invitationConsumer.seek(myJoinTopic, conf.joiningOffset)
                        println(s"przypisałem invitation consumerowi topic i offset")


                        var hasChats = conf.chats.nonEmpty

                        if ( hasChats ) {
                          val partitions: Iterable[(TopicPartition, Long)] = conf.chats.flatMap(
                            t => t.partitionOffset.map(r => (new TopicPartition(t.chatId, r.partition), r.offset))
                          )

                          // assign partitions of chat topic to kafka consumer
                          // messageConsumer.assign(CollectionConverters.asJava( partitions.map( t => t._1).toList ))


                          // we manually set offsets to read from topic and
                          // we start reading from it from last read message (offset)
                          // offset is always set as last read message offset + 1
                          // so we dont have duplicated messages.
                          partitions.foreach(t => messageConsumer.seek(t._1, t._2))
                          println(s"przypisałem message consumer wszystkie. topiki. i offsety")

                          // writing consumer topic settings
                          val writingTopics = conf.chats.map(t => Domain.generateWritingId(t.chatId))

                          writingConsumer.subscribe(CollectionConverters.asJavaCollection(writingTopics))

                          println(s"wszystkie consumery uruchomione. ")
                        }


                        // Start loop to read from topic
                        while (continueReading.get()) {

                          if (newChats.nonEmpty) {
                            // todo here lock on newChats ???

                            Try {
                              println(s"zaczynam dodawanie nowego chatu do listy")

                              val newPartitions: Iterable[(TopicPartition, Long)] = newChats.flatMap(
                                t => t._2.map(r => (new TopicPartition(t._1, r.partition), r.offset))
                              )
                              println(s"utworzyłem listę topicpartycji")
                              messageConsumer.assign(CollectionConverters.asJava(newPartitions.map(_._1).toList))
                              println(s"zrobiłem assign() do message consumera")
                              newPartitions.foreach(t => messageConsumer.seek(t._1, t._2))
                              println(s"zrobiłem seek()  do message consumera")


                              // writing topic actualisation
                              val wrtTopicSet = writingConsumer.listTopics().keySet()
                              println(s"writing topic set taken")
                              wrtTopicSet.addAll(CollectionConverters.asJavaCollection(newChats.keySet))
                              println(s"new chats id's added to writing topic set ")
                              writingConsumer.subscribe(wrtTopicSet)
                              println(s"writing consumer subscribed ")


                              newChats.clear()
                              println(s"!!! NEW CHAT ADDED !!!.")
                              hasChats = true
                            } match {
                              case Failure(exception) =>
                                println(s"ERROR: ${exception.getMessage}\n${exception.printStackTrace()}")
                                newChats.clear()
                              case Success(value) =>
                                println(s"Try succeeded.")
                            }


                          }

                          val invitations: ConsumerRecords[String, Invitation] = invitationConsumer.poll(java.time.Duration.ofMillis(2500))

                          invitations.forEach(
                            (r: ConsumerRecord[String, Invitation]) => {
                              val i: Invitation = r.value().copy(myJoiningOffset = Option(r.offset() + 1L))
                              println(s"wysyłanie zaproszenia")
                              out ! Invitation.toWebsocketJSON(i)
                            }
                          )

                          if ( hasChats ) {
                            val messages: ConsumerRecords[String, Message] = messageConsumer.poll(java.time.Duration.ofMillis(2500))
                            println(s"pool'owanie z message wykonane")
                            val writings: ConsumerRecords[String, Writing] = writingConsumer.poll(java.time.Duration.ofMillis(2500))
                            println(s"pool'owanie ze wszystkich wykonane")
                            messages.forEach(
                              (r: ConsumerRecord[String, Message]) => {
                                val m = (r.value().copy(serverTime = r.timestamp()), r.partition(), r.offset())
                                println(s"wysyłanie wiadomości")
                                out ! Message.toWebsocketJSON(m)
                              }
                            )
                            writings.forEach(
                              (r: ConsumerRecord[String, Writing]) => out ! Writing.toWebsocketJSON(r.value())
                            )
                          } // end of if
                        } // end of while loop
                        println(s"koniec Using(writingConsumer)")
                      }
                    }
                    println(s"koniec Using(messageConsumer)")
                  }
                }
                println(s"koniec Using(joiningConsumer)")
              }
            }
            if ( this.continueReading.get() ) {
              // TODO if we should continue reading but something goes wrong (lost connection to kafka etc)
              //  we should inform user and close actor
              //  if user got this special message should not start trying to reconnect
              //  because
              here // zmienić na obiekt JSON
              out ! ("Kafka connection lost. Try refresh page in a few minutes.")
              Thread.sleep(250)
              this.selfReference match {
                case Some(self) => self ! PoisonPill // switch off Actor
                case None => // do nothing
              }
            }

          }(ec)
        )
        out ! ("{\"comm\":\"opened correctly\"}")
      case None =>
        println(s"inicjalizacja konfiguracji NIE powiodła się.")
    }
  }

  def initializeChatConsumers(): Unit = {

  }


  def clearBroker(): Unit = {
    this.continueReading.set( false )
    val f1 = Future {
      this.messageProducer.close(Duration.ofSeconds(30L))
    }( ec )
//    val f2 = Future {
//      this.invitationProducer.close(Duration.ofSeconds(30L))
//    }(ec)
    val f3 = Future {
      this.writingProducer.close(Duration.ofSeconds(30L))
    }(ec)
    this.listener match {
      case Some(future) =>
        future.value match {
          case Some(someOrFailure) =>
            someOrFailure match {
              case Failure(exception) =>
                println(s"Exception in failure: ${exception.getMessage}, andStackTrace: " +
                  s"\n${exception.getStackTrace.mkString("Array(", ", ", ")")}")
              case Success(value) => println(s"future ok zwróciło $value")
            }
          case None => println(s"future ciągle działa")
        }
      case None =>
    }
    // Await.result(f1.zipWith(f2)((u1, u2) => u2)(ec), scala.concurrent.duration.Duration.create(5L, scala.concurrent.duration.SECONDS))
    this.ka.closeAdmin()
    println(s"BrokerExecutor is switched off. ")
  }


  def sendMessage(m: Message): Unit = {
    messageProducer.send(new ProducerRecord[String, Message](m.chatId, m))
  }


  def sendWriting(w: Writing): Unit = {
    val writingTopic = Domain.generateWritingId(w.chatId)
    writingProducer.send(new ProducerRecord[String, Writing](writingTopic, w))
  }


  def addNewChat(chat: ChatPartitionsOffsets): Unit = {
    this.newChats.addOne(chat.chatId, chat.partitionOffset)
  }


  def updateChatOffset(u: ChatOffsetUpdate): Unit = {
    here // tutaj trzeba jeszcze uwzględnić aby zmiany były tez wprowadzone w MAP chat.
    Future {
      val dbExecutor = new DbExecutor(new KafkaProductionConfigurator)
      db.withConnection( implicit connection => {
        dbExecutor.updateChatOffsetAndMessageTime(u.userId, u.chatId, u.lastMessageTime, u.partitionOffsets.toSeq )
      })
    }(ec)
  }

  def setSelfReference(self: ActorRef): Unit = this.selfReference = Option(self)











  @deprecated
  def sendInvitation(i: Invitation): Unit = {
    val joiningTopic = Domain.generateJoinId(i.toUserId)
    // invitationProducer.send(new ProducerRecord[String, Invitation](joiningTopic, i))
  }


  @deprecated
  def updateUserJoiningOffset(u: UserOffsetUpdate): Unit = {
    Future {
      val dbExecutor = new DbExecutor(new KafkaProductionConfigurator)
      db.withConnection( implicit connection => {
        dbExecutor.updateJoiningOffset(u.userId, u.joiningOffset)
      })
    }(ec)
  }

}
