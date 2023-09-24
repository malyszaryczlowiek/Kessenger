package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message, PartitionOffset, User}
import kafka.KafkaAdmin
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger




class OldMessageReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
                       ec: ExecutionContext, actorGroupID: UUID) extends Reader {


  private val chats:  TrieMap[ChatId, (List[PartitionOffset], List[PartitionOffset])] = TrieMap.empty
  private val logger: Logger = LoggerFactory.getLogger(classOf[OldMessageReader]).asInstanceOf[Logger]
  logger.trace(s"OldMessageReader. Starting reader. actorGroupID(${actorGroupID.toString})")

  this.chats.addAll(conf.chats.map(c => (c.chatId, (c.partitionOffset, c.partitionOffset))))


  override protected def initializeConsumer[User, Message](consumer: KafkaConsumer[User, Message]): Unit = {}



  override def startReading(): Unit = {}



  override def stopReading(): Unit = {
    logger.trace(s"stopReading. Reading stopped. actorGroupID(${actorGroupID.toString})")
  }



  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {
    this.chats.addOne(newChat.chatId -> (newChat.partitionOffset, newChat.partitionOffset))
    fetchOlderMessages(newChat.chatId)
    logger.trace(s"addNewChat. Adding new chat. actorGroupID(${actorGroupID.toString})")
  }



  override def fetchOlderMessages(chatId: ChatId): Unit = {
    Future {
      this.chats.get(chatId) match {
        case Some(t) =>
          Using(this.ka.createMessageConsumer(s"${this.conf.me.userId.toString}_old")) {
            consumer => {
              if (t._1.map(_.offset).sum > 0L) {
                val partitions: Iterable[(TopicPartition, Long)] =
                  fetchingOffsetShift(t._1).map(po => (new TopicPartition(chatId, po.partition), po.offset))
                logger.trace(s"fetchOlderMessages. Reading from chat: ${fetchingOffsetShift(t._1)}. actorGroupID(${actorGroupID.toString})")
                consumer.assign(CollectionConverters.asJava(partitions.map(tt => tt._1).toList))
                partitions.foreach(tt => consumer.seek(tt._1, tt._2))
                readToOffset(consumer, t._1)
                this.chats.get(chatId) match {
                  case Some(t) => this.chats.put(chatId, (fetchingOffsetShift(t._1), t._2))
                  case None =>
                }
              } else
                logger.trace(s"fetchOlderMessages. Cannot load older messages, offset is below 0. actorGroupID(${actorGroupID.toString})")
            }
          } match {
            case Failure(exception) =>
              logger.error(s"fetchOlderMessages. Exception thrown: ${exception.getMessage}. actorGroupID(${actorGroupID.toString})")
            case Success(_) =>
              logger.trace(s"fetchOlderMessages. Successfully closed consumer. actorGroupID(${actorGroupID.toString})")
          }

        case None =>
      }
    }(ec)

  }



  @tailrec
  private def readToOffset(consumer: KafkaConsumer[User, Message], maxPartOff: List[PartitionOffset]): Unit = {
    val messages: ConsumerRecords[User, Message] = consumer.poll(java.time.Duration.ofMillis(100L))
    val buffer = ListBuffer.empty[Message]
    logger.trace(s"OldMessageReader. number of messages: ${messages.count()}. actorGroupID(${actorGroupID.toString})")
    messages.forEach(
      (r: ConsumerRecord[User, Message]) => {
        val m = r.value().copy(serverTime = r.timestamp(), partOff = Some(PartitionOffset(r.partition(), r.offset())))
        maxPartOff.find(po => po.partition == r.partition() && po.offset > r.offset()) match {
          case Some(_) =>
            logger.trace(s"OldMessageReader. Message added to buffer. actorGroupID(${actorGroupID.toString})")
            buffer.addOne(m)
          case None =>
            logger.trace(s"OldMessageReader. Message older than threshold, NOT added to buffer. actorGroupID(${actorGroupID.toString})")
        }
      }
    )
    val messagesToSend = buffer.toList
    if (messagesToSend.nonEmpty) {
      logger.trace(s"OldMessageReader. Sending message to web-app. actorGroupID(${actorGroupID.toString})")
      out ! Message.toOldMessagesWebsocketJSON(messagesToSend)
      readToOffset(consumer, maxPartOff)
    }
  }



  private def fetchingOffsetShift(l: List[PartitionOffset]): List[PartitionOffset] = {
    l.map(po => {
      if (po.offset < 15L) PartitionOffset(po.partition, 0L)
      else PartitionOffset(po.partition, po.offset - 15L)
    })
  }



}

































