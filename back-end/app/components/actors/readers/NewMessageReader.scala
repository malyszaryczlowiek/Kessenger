package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model._
import kafka.KafkaAdmin
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}





class NewMessageReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
                       ec: ExecutionContext, actorGroupID: UUID) extends Reader {

  private val chats: TrieMap[ChatId, List[PartitionOffset]] = TrieMap.empty
  private val newChats: TrieMap[ChatId, List[PartitionOffset]] = TrieMap.empty
  private val continueReading: AtomicBoolean = new AtomicBoolean(true)
  private var fut: Option[Future[Unit]] = None
  private val logger: Logger = LoggerFactory.getLogger(classOf[NewMessageReader]).asInstanceOf[Logger]

  logger.trace(s"NewMessageReader. Starting reader. actorGroupID(${actorGroupID.toString})")

  this.chats.addAll(this.conf.chats.map(c => (c.chatId, c.partitionOffset)))

  startReading()


  override def startReading(): Unit = {
    if(this.chats.nonEmpty) this.fut = Option(futureBody())
  }


  private def futureBody(): Future[Unit] = {
    Future {
      Using(this.ka.createMessageConsumer(conf.me.userId.toString)) {
        consumer => {
          initializeConsumer(consumer)
          readingLoop(consumer)
        }
      } match {
        case Failure(exception) =>
          logger.error(s"NewMessageReader. Exception thrown: ${exception.getMessage}. actorGroupID(${actorGroupID.toString})")
          // if reading ended with error we need to close all actor system
          // and give a chance for web app to restart.
          out ! ResponseBody(44, "Kafka connection lost. Try refresh page in a few minutes.").toString
          Thread.sleep(250)
          parentActor ! PoisonPill
        case Success(_) =>
          logger.trace(s"NewMessageReader. Consumer closed normally. actorGroupID(${actorGroupID.toString})")
      }
    }(ec)
  }



  override protected def initializeConsumer[User, Message](consumer: KafkaConsumer[User, Message]): Unit = {
    val partitions: Iterable[(TopicPartition, Long)] = this.chats.flatMap(
      kv => kv._2.map(po => (new TopicPartition(kv._1, po.partition), po.offset))
    )
    consumer.assign(CollectionConverters.asJava(partitions.map(t => t._1).toList))
    // we manually set offsets to read from topic and
    // we start reading from it from last read message (offset)
    // offset is always set as last read message offset + 1
    // so we dont have duplicated messages.
    partitions.foreach(t => consumer.seek(t._1, t._2))
    logger.trace(s"NewMessageReader. Consumer initialized normally. actorGroupID(${actorGroupID.toString})")
  }



  @tailrec
  private def readingLoop(consumer: KafkaConsumer[User, Message]): Unit = {
    if (this.newChats.nonEmpty) reassignConsumer(consumer)
    if (this.continueReading.get()) {
      read(consumer)
      readingLoop(consumer)
    }
  }



  private def reassignConsumer(consumer: KafkaConsumer[User, Message]): Unit = {
    consumer.unsubscribe()
    newChats.foreach(kv => this.chats.put(kv._1, kv._2))
    val partitions: Iterable[(TopicPartition, Long)] = this.chats.flatMap(
      kv => kv._2.map(po => (new TopicPartition(kv._1, po.partition), po.offset))
    )
    consumer.assign(CollectionConverters.asJava(partitions.map(t => t._1).toList))
    // we manually set offsets to read from topic and
    // we start reading from it from last read message (offset)
    // offset is always set as last read message offset + 1
    // so we dont have duplicated messages.
    partitions.foreach(t => consumer.seek(t._1, t._2))
    newChats.clear()
  }



  private def read(consumer: KafkaConsumer[User, Message]): Unit = {
    val messages: ConsumerRecords[User, Message] = consumer.poll(java.time.Duration.ofMillis(250))
    val buffer = ListBuffer.empty[Message]
    messages.forEach(
      (r: ConsumerRecord[User, Message]) => {
        val m = r.value().copy(serverTime = r.timestamp(), partOff = Some(PartitionOffset(r.partition(), r.offset())))
        this.chats.get(m.chatId) match {
          case Some(po) =>
            val newPO = po.map(v => {
              if (v.partition == r.partition() && v.offset <= r.offset() )
                PartitionOffset(r.partition(), r.offset() + 1L)
              else v
            })
            this.chats.put(m.chatId, newPO)
          case None =>
        }
        buffer.addOne(m)
      }
    )
    val messagesToSend = buffer.toList
    if (messagesToSend.nonEmpty) out ! Message.toNewMessagesWebsocketJSON(messagesToSend)
  }



  override def stopReading(): Unit = {
    this.continueReading.set(false)
  }


  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {
    if (this.chats.isEmpty) {
      this.chats.addOne(newChat.chatId -> newChat.partitionOffset)
      logger.trace(s"NewMessageReader. Adding new chat, chat list is empty. actorGroupID(${actorGroupID.toString})")
      startReading()
    }
    else {
      logger.trace(s"NewMessageReader. Adding new chat, chat list is NOT empty. actorGroupID(${actorGroupID.toString})")
      this.newChats.addOne(newChat.chatId -> newChat.partitionOffset)
    }
  }



  override def fetchOlderMessages(chatId: ChatId): Unit = {}




}
