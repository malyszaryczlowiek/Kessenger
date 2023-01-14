package util

import akka.actor.ActorRef
import components.db.DbExecutor
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.{KafkaConfigurator, KafkaProductionConfigurator}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, Configuration, Invitation, Message, ResponseBody, UserOffsetUpdate, Writing}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import play.api.db.Database

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.Using

class BrokerExecutor(private var conf: Option[Configuration], private val out: ActorRef, private val db: Database, private val env: KafkaConfigurator, private val ec: ExecutionContext) { // (implicit s: String)

  private val ka: KessengerAdmin = new KessengerAdmin(env)


  /**
   * Field keeps if we should tracking incoming messages from
   * kafka broker.
   */
  private val continueReading: AtomicBoolean = new AtomicBoolean(true)


  /**
   * joinProducer object is responsible for sending invitation
   * messages to other users.
   */
  private val invitationProducer: KafkaProducer[String, Invitation] = ka.createInvitationProducer


  /**
   * kafka producer used to send messages to specific chat topic.
   */
  private val messageProducer: KafkaProducer[String, Message] = ka.createMessageProducer

  private val writingProducer: KafkaProducer[String, Writing] = ka.createWritingProducer

  private val newChats: collection.concurrent.TrieMap[ChatId, Map[Int, Long]] = collection.concurrent.TrieMap.empty



  // start invitation listener
  private var listener: Option[Future[Any]] = None


  def initialize(conf: Configuration): Unit = {
    this.conf = Option(conf)
    this.conf match {
      case Some( c ) =>
        // create joining topic to listen invitation from other users
//        if (conf.joiningOffset == -1L) {
//          this.ka.createInvitationTopic(conf.me.userId) match {
//            case Left(ke) =>
//              out ! ResponseBody(222, s"Kafka Error: ${ke.description} Cannot create joiningTopic. ").toString
//            case Right(_) =>
//              this.conf = Option(conf.copy(joiningOffset = 0L))
//              out ! ResponseBody(0, "Joining topic created.").toString
//          }
//        }
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
                        // invitationConsumer.assign(java.util.List.of(myJoinTopic))

                        // this may throw IllegalArgumentException if joining topic exists,
                        // but in db we do not have updated offset
                        // and inserted value of joinOffset (taken from db) is -1.

                        // and assign offset for that topic partition
                        invitationConsumer.seek(myJoinTopic, conf.joiningOffset)

                        // TODO zmienić tak aby pobierał po 10 ostatnich wiadomości

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

                        // writing consumer topic settings
                        val writingTopics = conf.chats.map(t => Domain.generateWritingId(t.chatId))
                        writingConsumer.subscribe(CollectionConverters.asJavaCollection( writingTopics ))

                        println(s"wszystkie consumery uruchomione. ")

                        // Start loop to read from topic
                        while (continueReading.get()) {

                          if (newChats.nonEmpty) {
                            val newPartitions: Iterable[(TopicPartition, Long)] = newChats.flatMap(
                              t => t._2.map(r => (new TopicPartition(t._1, r._1), r._2))
                            )
                            newPartitions.foreach(t => messageConsumer.seek(t._1, t._2))
                            // writing topic actualisation
                            val wrtTopicSet = writingConsumer.listTopics().keySet()
                            wrtTopicSet.addAll(CollectionConverters.asJavaCollection(newChats.keySet))
                            writingConsumer.subscribe( wrtTopicSet )
                            newChats.keySet.foreach(chatId => newChats.remove(chatId))
                            println(s"nowy chat dodany.")
                          }

                          val invitations: ConsumerRecords[String, Invitation] = invitationConsumer.poll(java.time.Duration.ofMillis(250))
                          val messages:    ConsumerRecords[String, Message]    = messageConsumer.poll(java.time.Duration.ofMillis(250))
                          val writings:    ConsumerRecords[String, Writing]    = writingConsumer.poll(java.time.Duration.ofMillis(250))
                          invitations.forEach(
                            (r: ConsumerRecord[String, Invitation]) => {
                              val i: Invitation = r.value().copy(myJoiningOffset = Option(r.offset() + 1L))
                              out ! Invitation.toWebsocketJSON(i)
                            }
                          )

                          messages.forEach(
                            (r: ConsumerRecord[String, Message]) => {
                              val m = (r.value().copy(serverTime = r.timestamp()), r.partition(), r.offset())
                              out ! Message.toWebsocketJSON(m)
                            }
                          )
                          writings.forEach(
                            (r: ConsumerRecord[String, Writing]) => out ! Writing.toWebsocketJSON( r.value() )
                          )

                        } // end of while loop
                      }
                    }
                  }
                }
              }
            }
          }(ec)
        )
      case None =>
        println(s"inicjalizacja konfiguracji NIE powiodła się.")
    }
  }




  def clearBroker(): Unit = {
    this.continueReading.set( false )
    val f1 = Future {
      this.messageProducer.close(Duration.ofSeconds(5L))
    }( ec )
    val f2 = Future {
      this.invitationProducer.close(Duration.ofSeconds(5L))
    }(ec)
    this.listener match {
      case Some(value) =>
      case None =>
    }
    // Await.result(f1.zipWith(f2)((u1, u2) => u2)(ec), scala.concurrent.duration.Duration.create(5L, scala.concurrent.duration.SECONDS))
    this.ka.closeAdmin()
    println(s"BrokerExecutor is switched off. ")
  }


  def sendMessage(m: Message): Unit = {
    messageProducer.send(new ProducerRecord[String, Message](m.chatId, m))
  }


  def sendInvitation(i: Invitation): Unit = {
    val joiningTopic = Domain.generateJoinId(i.toUserId)
    invitationProducer.send(new ProducerRecord[String, Invitation](joiningTopic, i))
  }


  def sendWriting(w: Writing): Unit = {
    val writingTopic = Domain.generateWritingId(w.chatId)
    writingProducer.send(new ProducerRecord[String, Writing](writingTopic, w))
  }


  def addNewChat(chatId: ChatId): Unit = {
    val offsets = (0 until env.CHAT_TOPIC_PARTITIONS_NUMBER).map(i => (i, 0L)).toMap
    this.newChats.addOne(chatId, offsets)
  }


  def updateChatOffset(u: ChatOffsetUpdate): Unit = {
    Future {
      val dbExecutor = new DbExecutor(new KafkaProductionConfigurator)
      db.withConnection( implicit connection => {
        dbExecutor.updateChatOffsetAndMessageTime(u.userId, u.chatId, u.lastMessageTime, u.partitionOffsets.toSeq )
      })
    }(ec)
  }


  def updateUserJoiningOffset(u: UserOffsetUpdate): Unit = {
    Future {
      val dbExecutor = new DbExecutor(new KafkaProductionConfigurator)
      db.withConnection( implicit connection => {
        dbExecutor.updateJoiningOffset(u.userId, u.joiningOffset)
      })
    }(ec)
  }

}
