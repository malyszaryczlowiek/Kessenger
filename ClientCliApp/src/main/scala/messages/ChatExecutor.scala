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
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.Future
import scala.collection.immutable
import collection.parallel.CollectionConverters.MutableMapIsParallelizable
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}




class ChatExecutor(me: User, chat: Chat, chatUsers: List[User]):

  private val continueReading:  AtomicBoolean = new AtomicBoolean(true)
  private val newOffset:           AtomicLong = new AtomicLong( chat.offset )
  private val lastMessageTime:     AtomicLong = new AtomicLong( TimeConverter.fromLocalToEpochTime(chat.timeOfLastMessage) )
  private val printMessage:     AtomicBoolean = new AtomicBoolean(false)

  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  private val topicPartition: TopicPartition                = new TopicPartition(chat.chatId, 0)
  private val chatProducer:   KafkaProducer[String, String] = KessengerAdmin.createChatProducer

  private val unreadMessages: ParTrieMap[Long,(Login, LocalDateTime, String)] = ParTrieMap.empty[Long, (Login, LocalDateTime, String)]


  def sendMessage(message: String): Unit =
    chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message)) // , callBack)


  private var chatReader: Option[Future[Unit]] = Some(createChatReader())

  private def createChatReader(): Future[Unit] =
    val future = Future {
      val chatConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)
      // assign specific topic to read from
      chatConsumer.assign(java.util.List.of(topicPartition))
      // we manually set offset to read from
      chatConsumer.seek(topicPartition, newOffset.get()) // we start reading from topic from last read message (offset)
      while (continueReading.get()) {
        val records: ConsumerRecords[String, String] = chatConsumer.poll(Duration.ofMillis(250))
        records.forEach(
          (r: ConsumerRecord[String, String]) => {
            val senderUUID = UUID.fromString(r.key())
            val login = chatUsers.find(_.userId == senderUUID) match
              case Some(user) => user.login
              case None       => "Deleted User"
            // https://docs.oracle.com/javase/tutorial/datetime/iso/timezones.html
            // val time =
            //lastMessageTime.set(r.timestamp())
            if printMessage.get() then
              printMessage(login, r.timestamp(), r.value(), r.offset())
            else
              showNotification(login, r.timestamp(), r.value(), r.offset())
          }
        )
      }
      chatConsumer.close()
    }
    future.onComplete {
      case Failure(ex)    =>
        // if some error occurred we restart chatReader
        if continueReading.get() then
          // try to restart every 3 seconds
          Thread.sleep(3000)
          chatReader = Some( createChatReader() )
      case Success(value) =>
        println(s"Chat ${chat.chatName} closed.")
    }
    future



  private def restartChatReader(): Unit =
    if chatReader.get.isCompleted then
      chatReader = Some( createChatReader() )



  def stopPrintMessages(): Unit = printMessage.set(false)



  /**
   * Note:
   * Notifications do not change offset,
   * because message is still not read.
   * @param records
   */
  private def showNotification(login: Login, timeStamp: Long, message: String, offset: Long): Unit =
    if login == me.login then ()
    else
      println(s"One new message from $login in ${chat.chatName}.") // print notification
      val time: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( timeStamp )
      unreadMessages.addOne((offset,(login, time, message)))


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
    printMessage.set(true)
    // if from some reasons chat reader is not running
    // but we have some unread messages we print them
    // otherwise we print them via printMessage() method
    // in chatReader future.
    if chatReader.get.isCompleted then
      val map: immutable.SortedMap[Long, (Login, LocalDateTime, String)] =
        unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
      unreadMessages.clear() // clear off ParSequence
      map.foreach( (k,v) => {
        newOffset.set(k)
        // this prints messages to user
        println(s"${v._1} ${v._2} >> ${v._3}")
      } )



  // this method is not used currently
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
          case Some(u: User) => u.login
          case None          => "Deleted User"
        printMessage(login, r.timestamp(), r.value(), r.offset()) // TODO watch out on offset
      }
    )
    nMessageConsumer.close()  // we close message consumer.



  private def printMessage(login: Login, timeStamp: Long, message: String, offset: Long): Unit =
    val localTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( timeStamp )
    if unreadMessages.isEmpty then
      if login == me.login then ()
        // we not print own messages
      else println(s"$login $localTime >> $message")
      newOffset.set( offset )
      lastMessageTime.set( timeStamp )
    else
      val map: immutable.SortedMap[Long,(Login, LocalDateTime, String)] = unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
      unreadMessages.clear() // clear off ParSequence
      map.foreach( (k,v) => {
        println(s"${v._1} ${v._2} >> ${v._3}")
        newOffset.set(k)
        lastMessageTime.set( TimeConverter.fromLocalToEpochTime( v._2 ) )
      } )
      println(s"$login $localTime >> $message") // and finally print last message.
      newOffset.set( offset )
      lastMessageTime.set( timeStamp )



  def getLastMessageTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( lastMessageTime.get() )

  //def getLastReadMessageMilliSecondsTime: Long = lastReadMessageTime.get()

  def getChatOffset: Long = newOffset.get()

  
  def getUser: User = me

  /**
   * 
   * @return Returns updated chat with last read message offset
   *         and Last Read message Local Time.
   */
  def getChat: Chat = 
    Chat(chat.chatId, chat.chatName, chat.groupChat, newOffset.get(), TimeConverter.fromMilliSecondsToLocal(lastMessageTime.get()))


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
    getChat











