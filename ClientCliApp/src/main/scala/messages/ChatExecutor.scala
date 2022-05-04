package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain.{ChatName, Login, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.util.TimeConverter
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import concurrent.ExecutionContext.Implicits.global
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.Future
// import scala.reflect.internal.util.Collections
import collection.parallel.CollectionConverters.MutableMapIsParallelizable
import scala.collection.immutable




class ChatExecutor(me: User, chat: Chat, chatUsers: List[User]):

  private val continueReading: AtomicBoolean = new AtomicBoolean(true)
  private val newOffset:       AtomicLong    = new AtomicLong(chat.offset)
  private val epochTime:       AtomicLong    = new AtomicLong( TimeConverter.fromLocalToEpochTime(chat.timeOfLastMessage) )
  private val printMessage:    AtomicBoolean = new AtomicBoolean(false)

  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  private val topicPartition: TopicPartition                = new TopicPartition(chat.chatId, 0)
  private val chatProducer:   KafkaProducer[String, String] = KessengerAdmin.createChatProducer()
  private val chatConsumer:   KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)

  private val unreadMessages: ParTrieMap[Long,(Login, LocalDateTime, String)] = ParTrieMap.empty[Long, (Login, LocalDateTime, String)]

  chatConsumer.assign(java.util.List.of(topicPartition))
  chatConsumer.seek(topicPartition, chat.offset) // we start reading from topic from last read message (offset)


  def sendMessage(message: String): Unit =
    chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message)) // , callBack)


  private val chatReader = Future {
    while (continueReading.get()) {
      val records: ConsumerRecords[String, String] = chatConsumer.poll(Duration.ofMillis(250))
      records.forEach(
        (r: ConsumerRecord[String, String]) => {
          val senderUUID = UUID.fromString(r.key())
          val login = chatUsers.find(_.userId == senderUUID) match
            case Some(user) => user.login
            case None       => "Deleted User"
          // https://docs.oracle.com/javase/tutorial/datetime/iso/timezones.html
          val time = TimeConverter.fromMilliSecondsToLocal(r.timestamp())
          epochTime.set(r.timestamp())
          if printMessage.get() then printMessage(login, time, r.value(), r.offset())
          else showNotification(login, time, r.value(), r.offset())
        })
    }
  }


  def stopPrintingMessages():  Unit = printMessage.set(false)


  private def printMessage(login: Login, time: LocalDateTime, message: String, offset: Long): Unit =
    if login == me.login then ()
    else println(s"$login $time >> $message")
    newOffset.set(offset)



  // private def reloadChatUsers(): List[User] = ???
  //  ExternalDB.

  /**
   * Note:
   * Notifications do not change offset,
   * because message is still not read.
   * @param records
   */
  private def showNotification(login: Login, time: LocalDateTime, message: String, offset: Long): Unit =
    if login == me.login then ()
    else unreadMessages.addOne((offset,(login, time, message)))


  /**
   * This method is safe for unreadMessages. We can transform
   * our ParMap to SortedMap and then clear unreadMessages and dont worry
   * that between this two calls some item will be added to unreadMessages,
   * because before we call this message in ProgramExecutor
   * we call startPrintingMessages(), so
   * printMessage is set to true and
   * and printMessage() is starting execute in chatReader
   * instead of showNotification(),
   * and so unreadMessages is not modified in this time.
   */
  def printUnreadMessages(): Unit =
    val map: immutable.SortedMap[Long,(Login, LocalDateTime, String)] = unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
    unreadMessages.clear() // clear off ParSequence
    map.foreach( (k,v) => printMessage(v._1, v._2, v._3, k) )
    printMessage.set(true)


  def showLastNMessages(n: Long): Unit =
    val nMessageConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)
    nMessageConsumer.assign(java.util.List.of(topicPartition))
    var readFrom = newOffset.get() - n
    if readFrom < 0L then readFrom = 0L
    nMessageConsumer.seek(topicPartition, readFrom) // we start reading from topic from last read message (offset) minus n
    val records: ConsumerRecords[String, String] = nMessageConsumer.poll(Duration.ofMillis(250))
    records.forEach(
      (r: ConsumerRecord[String, String]) => {
        val senderUUID = UUID.fromString(r.key())
        val login = chatUsers.find(_.userId == senderUUID) match
          case Some(user: User) => user.login
          case None       => "Deleted User"
        // https://docs.oracle.com/javase/tutorial/datetime/iso/timezones.html
        val time = TimeConverter.fromMilliSecondsToLocal(r.timestamp())
        printMessage(login, time, r.value(), r.offset()) // TODO watch out on offset
      }
    )
    nMessageConsumer.close()  // we close message consumer.



  def getLastMessageTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( epochTime.get() )

  
  def getUser: User = me
  def getChat: Chat = chat


  /**
   * Method closes producer, closes consumer loop in another thread,
   * with closing that thread and closes consumer object.
   *
   * @return returns chat object with updated offset,
   *         which must be saved to DB subsequently.
   */
  def closeChat(): Chat =
    chatProducer.close()
    continueReading.set(false)
    chatReader.onComplete(t => {
      chatConsumer.close()
      println(s"Chat ${chat.chatName} closed.")
    })
    Chat(chat.chatId, chat.chatName, chat.groupChat, newOffset.get(), TimeConverter.fromMilliSecondsToLocal(epochTime.get()))












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
