package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message, PartitionOffset}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import util.KafkaAdmin

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Try, Using}




class OldMessageReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin, ec: ExecutionContext) extends Reader {


  private val chats:    TrieMap[ChatId, (List[PartitionOffset], List[PartitionOffset])] = TrieMap.empty
  // private val consumer: KafkaConsumer[String, Message] = this.ka.createMessageConsumer(s"${this.conf.me.userId.toString}_old")

  this.chats.addAll(conf.chats.map(c => (c.chatId, (c.partitionOffset, c.partitionOffset))))


  override protected def initializeConsumer[Message](consumer: KafkaConsumer[String, Message]): Unit = {}



  override def startReading(): Unit = {}



  override def stopReading(): Unit = {
    println(s"OldMessageReader --> stopReading() ended normally.")
//    this.consumer.unsubscribe()
//    Try {
//      this.consumer.close(java.time.Duration.ofMillis(5000L))
//      println(s"OldMessageReader --> stopReading() ended normally.")
//    }
  }



  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {
    this.chats.addOne(newChat.chatId -> (newChat.partitionOffset, newChat.partitionOffset))
    fetchOlderMessages(newChat.chatId)
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
                println(s"OldMessageReader --> Wczytję od: ${fetchingOffsetShift(t._1)}")
                consumer.assign(CollectionConverters.asJava(partitions.map(tt => tt._1).toList))
                partitions.foreach(tt => consumer.seek(tt._1, tt._2))
                readToOffset(consumer, t._1)
                this.chats.get(chatId) match {
                  case Some(t) => this.chats.put(chatId, (fetchingOffsetShift(t._1), t._2))
                  case None =>
                }
              } else println(s"OldMessageReader --> Cannot load older messages. Sum of offset is 0.")
            }
          } match {
            case Failure(exception) =>
              println(s"OldMessageReader --> Future EXCEPTION ${exception.getMessage}")
            case Success(_) =>
              println(s"OldMessageReader --> Successfully closed Future")
          }

        case None =>
      }
    }(ec)

  }



  @tailrec
  private def readToOffset(consumer: KafkaConsumer[String, Message], maxPartOff: List[PartitionOffset]): Unit = {
    val messages: ConsumerRecords[String, Message] = consumer.poll(java.time.Duration.ofMillis(100L))
    val buffer = ListBuffer.empty[Message]
    println(s"OldMessageReader --> Liczba Wiadomość ${messages.count()} .")
    messages.forEach(
      (r: ConsumerRecord[String, Message]) => {
        val m = r.value().copy(serverTime = r.timestamp(), partOff = Some(PartitionOffset(r.partition(), r.offset())))
        maxPartOff.find(po => po.partition == r.partition() && po.offset > r.offset()) match {
          case Some(_) =>
            println(s"OldMessageReader --> Wiadomość dodana do bufforu.")
            buffer.addOne(m)
          case None =>
            println(s"OldMessageReader --> Wiadomość jest nowsza niż current limit.")
        }
      }
    )
    val messagesToSend = buffer.toList
    if (messagesToSend.nonEmpty) {
      println(s"OldMessageReader --> Wsyłam wiadomości $messagesToSend")
      out ! Message.toOldMessagesWebsocketJSON(messagesToSend)
      readToOffset(consumer, maxPartOff)
    }
  }



  private def fetchingOffsetShift(l: List[PartitionOffset]): List[PartitionOffset] = {
    l.map(po => {
      if (po.offset < 5L) PartitionOffset(po.partition, 0L)
      else PartitionOffset(po.partition, po.offset - 5L)
    })
  }



}

































