package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.User
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.Duration
import java.util.UUID
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChatExecutor(me: User, chat: Chat):

  private val chatProducer: KafkaProducer[String, String] = KessengerAdmin.createChatProducer()
  private val chatConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(UUID.randomUUID().toString)
  // TODO it dosent matter what consumer group is but must be different from other

  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  val topicPartition = new TopicPartition(chat.chatId, 0)

  // we start reading from topic from last read message (offset)
  chatConsumer.seek(topicPartition, chat.offset)

  // in this example we dont care about
  // atomicity of this variable. if we want to stop
  // consumer loop simply change this value to false
  var continueReading = true
  var newOffset: Long = chat.offset // we can try to rewrite to AtomicLong

  private val future = Future {
    while (continueReading) {
      val records: ConsumerRecords[String, String] = chatConsumer.poll(Duration.ofMillis(100))
      records.forEach(
        (r: ConsumerRecord[String, String]) => {
          println(s"${r.key()} ${r.timestamp()}> ${r.value()}")
          newOffset = r.offset()
        }
      )
    }
  }

  def sendMessage(message: String): Unit = //???
    chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message), callBack)











  private val callBack: Callback = (metadata: RecordMetadata, exception: Exception) => {
    Option(exception) match {
      case Some(ex) =>
        // prints for debugging perpouces
        println(s"Exception during message sent to chat: ${ex.getMessage}")
      case None =>
        newOffset = metadata.offset()
        //print(s"Message sent correctly: Topic: ${metadata.topic()}, Timestamp: ${metadata.timestamp()}, OffSet: ${metadata.offset()}, partition: ${metadata.partition()}\n> ")
    }
  }


  /**
   * Method closes producer, closes consumer loop in another thread,
   * with closing that thread and closes consumer object.
   *
   * @return returns chat object with updated offset,
   *         which must be saved to DB subsequently.
   */
  def closeChat(): Chat =
    chatProducer.close()
    continueReading = false
    future.onComplete(t => {
      chatConsumer.close()
      println(s"Chat ${chat.chatName} closed.")
    })
    Chat(chat.chatId, chat.chatName, chat.groupChat, newOffset)