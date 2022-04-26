package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain.UserID
import com.github.malyszaryczlowiek.domain.User
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.internal.util.Collections


// TODO co w przypadku gdy user nie jest już aktywnym uczestnikiem czatu i
//   jego profil nie pojawi się w chatUsers?
//   trzeba zdefiniować czy użytkownik jest active

class ChatExecutor(me: User, chat: Chat, chatUsers: List[User]):

  private val chatProducer: KafkaProducer[String, String] = KessengerAdmin.createChatProducer()
  private val chatConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)

  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  val topicPartition = new TopicPartition(chat.chatId, 0)

  chatConsumer.assign(java.util.List.of(topicPartition)) //
  chatConsumer.seek(topicPartition, chat.offset) // we start reading from topic from last read message (offset)

  // in this example we dont care about
  // atomicity of this variable. if we want to stop
  // consumer loop simply change this value to false
  var continueReading = true
  var newOffset: Long = chat.offset // we can try to rewrite to AtomicLong

  private val future = Future {
    while (continueReading) {
      val records: ConsumerRecords[String, String] = chatConsumer.poll(Duration.ofMillis(250))
      records.forEach(
        (r: ConsumerRecord[String, String]) => {
          val senderUUID = UUID.fromString(r.key())
          val login = chatUsers.find(_.userId == senderUUID) match
            case Some(user) => user.login
            case None    => "Not Participating User"
          // https://docs.oracle.com/javase/tutorial/datetime/iso/timezones.html
          val time = LocalDateTime.ofInstant( Instant.ofEpochSecond(r.timestamp()/1000), ZoneId.systemDefault() )
          if login == me.login then ()
          else println(s"$login $time >> ${r.value()}")
          newOffset = r.offset()
        })}}

  def sendMessage(message: String): Unit = //???
    chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message))
    //chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message), callBack)


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












//private val callBack: Callback = (metadata: RecordMetadata, exception: Exception) => {
//  Option(exception) match {
//    case Some(ex) =>
//      // prints for debugging perpouces
//      println(s"Exception during message sent to chat: ${ex.getMessage}")
//    case None =>
//      println("Callback> message send.")
//      newOffset = metadata.offset()
//    //print(s"Message sent correctly: Topic: ${metadata.topic()}, Timestamp: ${metadata.timestamp()}, OffSet: ${metadata.offset()}, partition: ${metadata.partition()}\n> ")
//  }
//}
