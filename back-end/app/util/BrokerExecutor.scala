package util

import akka.actor.ActorRef

import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaConfigurator
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Invitation, Message}

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.Using

class BrokerExecutor(private var conf: Option[Configuration], private val out: ActorRef, private val env: KafkaConfigurator, ec: ExecutionContext) { // (implicit s: String)


  // here we initalize objct

  private val ka: KessengerAdmin = new KessengerAdmin(env)


  /**
   * Field keeps if we should tracking incomming messages from
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

  private val newChats: collection.concurrent.TrieMap[ChatId, Map[Int, Long]] = collection.concurrent.TrieMap.empty



  // start invitation listener

  private var listener: Option[Future[Any]] = None



  def initialize(conf: Configuration) = {
    this.conf = Option(conf)
    this.conf match {
      case Some( c ) =>
        // create joining topic to listen invitation from other users
        if (conf.joiningOffset == -1L) {
          this.ka.createInvitationTopic(conf.me.userId) match {
            case Left(ke) =>
              // todo tutaj prawdopodobnie trzeba zamknąć kafkę ???

              out ! (s"Kafka Error: ${ke.description}")
            case Right(_) =>
              this.conf = Option(conf.copy(joiningOffset = 0L))
              out ! (s"Joining topic created. ")
          }
        }

        this.listener = Option(
          Future {

            // at the beginning we create kafka consumer to consume invitation
            // from our joining topic.
            Using(this.ka.createInvitationConsumer(conf.me.userId.toString) ) {
              (invitationConsumer: KafkaConsumer[String, Invitation] ) =>
                Using(this.ka.createChatConsumer(conf.me.userId.toString)) {
                  (messageConsumer: KafkaConsumer[String, Message]) =>

                    // we set topic to read from (this topic has only one partition)
                    val myJoinTopic = new TopicPartition(Domain.generateJoinId(conf.me.userId), 0)

                    // assign this toopic to kafka consumer
                    // invitationConsumer.assign(java.util.List.of(myJoinTopic))

                    // this may throw IllegalArgumentException if joining topic exists,
                    // but in db we do not have updated offset
                    // and inserted value of joinOffset (taken from db) is -1.

                    // and assign offset for that topic partition
                    invitationConsumer.seek(myJoinTopic, conf.joiningOffset)


                    val partitions: Iterable[(TopicPartition, Long)] = conf.chats.flatMap(
                      t => t._2.map(r => (new TopicPartition(t._1, r._1), r._2))
                    )

                    // assign partitions of chat topic to kafka consumer
                    // messageConsumer.assign(CollectionConverters.asJava( partitions.map( t => t._1).toList ))


                    // we manually set offsets to read from topic and
                    // we start reading from it from last read message (offset)
                    // offset is always set as last read message offset + 1
                    // so we dont have duplicated messages.
                    partitions.foreach( t => messageConsumer.seek(t._1, t._2) )


                    // Start loop to read from topic
                    while (continueReading.get()) {

                      if (newChats.nonEmpty) {
                        val newPartitions: Iterable[(TopicPartition, Long)] = newChats.flatMap(
                          t => t._2.map(r => (new TopicPartition(t._1, r._1), r._2))
                        )
                        newPartitions.foreach( t => messageConsumer.seek(t._1, t._2) )
                        newChats.keySet.foreach(
                          chatId => newChats.remove(chatId) // usunąć wszystkie ChatId, które aktualnie znajdują się w mapie
                        )
                      }

                      /*
                      TODO
                       zaimplementować w kessenger-lib serializer i deserializer klasy Invitatio
                       */

                      val invitations: ConsumerRecords[String, Invitation] = invitationConsumer.poll(java.time.Duration.ofMillis(250))
                      val messages: ConsumerRecords[String, Message] = messageConsumer.poll(java.time.Duration.ofMillis(250))
                      invitations.forEach(
                        (r: ConsumerRecord[String, Invitation]) => {
                          val i: Invitation = r.value().copy(myJoiningOffset = Option(r.offset() + 1L))
                          out ! Invitation.toWebsocketJSON(i)
                        }
                      )
                      messages.forEach(
                        (r: ConsumerRecord[String, Message]) => {
                          val m = (r.value(), r.partition(), r.offset())
                          out ! Message.toWebsocketJSON( m )
                        }
                      )
                    } // end of while loop
                }
            }
          }( ec )
        )
      case None =>
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

      case None => ???
    }


    // zamknąć też wszystkie wątki, które mają uruchomione consumery
    // zamknąć też te consumery. ale wewnątrz wątków. sprawdzić jak to było zaimplementowane.

    this.ka.closeAdmin()
  }





  def sendMessage(m: Message): Unit = {
    messageProducer.send(new ProducerRecord[String, Message](m.chatId, m)) // , callBack)
  }




  def sendInvitation(i: Invitation): Unit = {
    val joiningTopic = Domain.generateJoinId(i.toUserId)
    invitationProducer.send(new ProducerRecord[String, Invitation](joiningTopic, i)) // , callBack)
  }


  def addNewChat(chatId: ChatId): Unit = {
    val offsets = (0 until env.CHAT_TOPIC_PARTITIONS_NUMBER).map(i => (i, 0L)).toMap
    this.newChats.addOne(chatId, offsets)
  }



}
