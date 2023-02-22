package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message, PartitionOffset}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import util.KessengerAdmin

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.jdk.javaapi.CollectionConverters
import scala.util.Try






class OldMessageReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KessengerAdmin, ec: ExecutionContext) extends Reader {


  private val chats:    TrieMap[ChatId, (List[PartitionOffset], List[PartitionOffset])] = TrieMap.empty
  private val consumer: KafkaConsumer[String, Message] = this.ka.createMessageConsumer(s"${this.conf.me.userId.toString}_old")

  this.chats.addAll(conf.chats.map(c => (c.chatId, (c.partitionOffset, c.partitionOffset))))





  override protected def initializeConsumer[Message](consumer: KafkaConsumer[String, Message]): Unit = {}


  private def fetchingOffsetShift(l: List[PartitionOffset]): List[PartitionOffset] = {
    l.map(po => {
      if (po.offset < 5L) PartitionOffset(po.partition, 0L)
      else PartitionOffset(po.partition, po.offset - 5L)
    })
  }

  override def startReading(): Unit = {}


  override def stopReading(): Unit = {
    this.consumer.unsubscribe()
    Try { this.consumer.close() }
  }



  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {
    this.chats.addOne(newChat.chatId -> (newChat.partitionOffset, newChat.partitionOffset))
    fetchOlderMessages(newChat.chatId)
  }



  override def fetchOlderMessages(chatId: ChatId): Unit = {
    this.chats.get(chatId) match {
      case Some( t ) =>
        val partitions: Iterable[(TopicPartition, Long)] =
          fetchingOffsetShift(t._1).map(po => (new TopicPartition(chatId, po.partition), po.offset))
        consumer.assign(CollectionConverters.asJava(partitions.map(tt => tt._1).toList))
        partitions.foreach(tt => consumer.seek(tt._1, tt._2))
        readFromChat(t._1)
        this.chats.get(chatId) match {
          case Some(t) => this.chats.put(chatId, (fetchingOffsetShift(t._1), t._2))
          case None =>
        }
        consumer.unsubscribe()
      case None =>
    }
  }



  @tailrec
  private def readFromChat(maxPartOff: List[PartitionOffset]): Unit = {
    val messages: ConsumerRecords[String, Message] = consumer.poll(java.time.Duration.ofMillis(0L))
    val buffer = ListBuffer.empty[Message]
    messages.forEach(
      (r: ConsumerRecord[String, Message]) => {
        maxPartOff.find(po => po.partition == r.partition() && po.offset > r.offset()) match {
          case Some(_) =>
            buffer.addOne(r.value().copy(serverTime = r.timestamp(), partOff = Some(PartitionOffset(r.partition(), r.offset()))))
          case None =>
        }
      }
    )
    val messagesToSend = buffer.toList
    if (messagesToSend.nonEmpty) {
      // filter not older than lower bandwidth
      out ! Message.toOldMessagesWebsocketJSON(messagesToSend)
      readFromChat(maxPartOff)
    }
  }




}

































