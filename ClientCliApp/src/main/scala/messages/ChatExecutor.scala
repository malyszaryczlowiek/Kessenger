package com.github.malyszaryczlowiek
package messages

import db.ExternalDB

import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.domain.Domain.*
import kessengerlibrary.util.TimeConverter

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.lang.Long as jLong
import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.{Await, Future}
import scala.collection.immutable
import scala.util.{Failure, Success}
import concurrent.ExecutionContext.Implicits.global




class ChatExecutor(me: User, chat: Chat, chatUsers: List[User]):

  private val continueReading:  AtomicBoolean = new AtomicBoolean(true)
  private val newOffset:           AtomicLong = new AtomicLong( chat.offset )
  private val lastMessageTime:     AtomicLong = new AtomicLong( TimeConverter.fromLocalToEpochTime(chat.timeOfLastMessage) )
  private val printMessage:     AtomicBoolean = new AtomicBoolean(false)

  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  private var chatProducer:   KafkaProducer[String, String] = KessengerAdmin.createChatProducer

  // (offset, (login, date, message string))
  private val unreadMessages: ParTrieMap[Long,(Login, LocalDateTime, String)] = ParTrieMap.empty[Long, (Login, LocalDateTime, String)]

  private var chatReader: Option[Future[Unit]] = Some( createChatReader() )


  def sendMessage(message: String): Unit =
    Future {
      val fut = chatProducer.send(new ProducerRecord[String, String](chat.chatId, me.userId.toString, message)) // , callBack)
      val result = fut.get(5L, TimeUnit.SECONDS)
      newOffset.set( result.offset() + 1L )
      lastMessageTime.set( result.timestamp() )
      updateDB()
    }




  private def createChatReader(): Future[Unit] =
    val future = Future {
      val chatConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)
      // val topicPartition0: TopicPartition                = new TopicPartition(chat.chatId, 0)
//      val topicPartition1: TopicPartition                = new TopicPartition(chat.chatId, 1)
//      val topicPartition2: TopicPartition                = new TopicPartition(chat.chatId, 2)

      // chatConsumer.commitSync() // todo try next

      chatConsumer.subscribe(java.util.List.of(chat.chatId))
      // assign specific topic to read from
      // chatConsumer.assign(java.util.List.of(topicPartition0))
      // we manually set offset to read from and
      // we start reading from topic from last read message (offset)
      //chatConsumer.seek(topicPartition0, newOffset.get() )  // TODO
//      chatConsumer.seek(topicPartition1, newOffset.get() )  // TODO
//      chatConsumer.seek(topicPartition2, newOffset.get() )  // TODO

      while (continueReading.get()) {
        val records: ConsumerRecords[String, String] = chatConsumer.poll(Duration.ofMillis(250))
        records.forEach(
          (record: ConsumerRecord[String, String]) => {
            if printMessage.get() then printMessage( record )
            else showNotification( record )
          }
        )
      }
      chatConsumer.close()
    }
    future.onComplete {
      case Failure(ex)    =>
        // if some error occurred we restart chatReader, every 3 seconds
        if continueReading.get() then
          Thread.sleep(3000)
          println(s"chat executor restarted.") // TODO DELETE
          chatReader = Some( createChatReader() )  // watch out of infinite loop when future ends so fast that  onComplete is not defined yet
        else updateDB()
      case Success(value) => updateDB()
        // after work we should save new offset and time
    }
    future



  private def restartChatReader(): Unit =
    if chatReader.get.isCompleted then
      chatReader = Some( createChatReader() )



  def stopPrintMessages(): Unit =
    printMessage.set(false)
    Future { updateDB() }



  /**
   * Note:
   * Notifications do not change offset,
   * because message is still not read.
   */
  private def showNotification(r: ConsumerRecord[String, String]): Unit =
    val senderUUID = UUID.fromString(r.key())
    val login = chatUsers.find(_.userId == senderUUID) match
      case Some(user) => user.login
      case None       => "Deleted User"
    if login != me.login then
      print(s"One new message from $login in chat '${chat.chatName}'.\n> ") // print notification
      val time: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )
      //lastUnreadOffset.set( r.offset() + 1L )
      unreadMessages.addOne((r.offset(),(login, time, r.value())))


  /**
   * This method is safe for unreadMessages. We can transform
   * our ParMap to SortedMap and then clear unreadMessages and dont worry
   * that between these two calls some item will be added to unreadMessages,
   * because before we call this message in ProgramExecutor
   * we call startPrintingMessages(), so
   * printMessage is set to true and
   * and printMessage() is starting execute in chatReader
   * instead of showNotification(),
   * and so, unreadMessages is not modified in this time.
   */
  def printUnreadMessages(): Unit =
    val map: immutable.SortedMap[Long, (Login, LocalDateTime, String)] =
      unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
    map.foreach( (k,v) => {
      newOffset.set( 1L + k )
      lastMessageTime.set( TimeConverter.fromLocalToEpochTime(v._2) )
      // this prints messages to user
      print(s"${v._1} ${v._2} >> ${v._3}\n> ")
    } )
    if map.isEmpty then print("> ")
    unreadMessages.clear() // clear off ParSequence
    printMessage.set(true)
    Future { updateDB() }
    // if from some reasons chat reader is not running
    // but we have some unread messages we print them
    // otherwise we print them via printMessage() method
    // in chatReader future.


  /**
   * This method is always called from external thread (not main),
   * so we can coll updateDB directly in it.
   */
  private def printMessage(r: ConsumerRecord[String, String]): Unit =
    val localTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )
    val senderUUID = UUID.fromString(r.key())
    val login = chatUsers.find(_.userId == senderUUID) match
      case Some(user) => user.login
      case None       => "Deleted User"
    val message = r.value()
    if unreadMessages.isEmpty then
      if login != me.login then print(s"$login $localTime >> $message\n> ")
      newOffset.set( r.offset() + 1L )
      lastMessageTime.set( r.timestamp() )
    else
      val map: immutable.SortedMap[Long,(Login, LocalDateTime, String)] =
        unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
      map.foreach( (k,v) => if login != me.login then print(s"${v._1} ${v._2} >> ${v._3}\n> ") )
      unreadMessages.clear() // clear off ParSequence
      print(s"$login $localTime >> $message\n> ") // and finally print last message.
      newOffset.set( r.offset() + 1L )
      lastMessageTime.set( r.timestamp() )
    updateDB()




  def getLastMessageTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( lastMessageTime.get() )


  // def getChatOffset: Long = newOffset.get()

  
  def getUser: User = me

  /**
   *
   * @return Returns updated chat with last read message offset
   *         and Last Read message Local Time.
   */
  def getChat: Chat =
    Chat(chat.chatId, chat.chatName, chat.groupChat, newOffset.get(), TimeConverter.fromMilliSecondsToLocal(lastMessageTime.get())) ///newOffset.get()

  private def updateDB(): Unit =
    ExternalDB.updateChatOffsetAndMessageTime(me, Seq(getChat)) match {
      case Left(queryErrors: QueryErrors) =>
        // queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
        // print(s"Leave the chat, and back in a few minutes.\n> ")
      case Right(value) =>
      // we do not need to notify user (sender)
      // that some values were updated in DB.
    }


  // this method is not used currently
//  def showLastNMessages(n: Long): Unit =
//    val nMessageConsumer: KafkaConsumer[String, String] = KessengerAdmin.createChatConsumer(me.userId.toString)
//    nMessageConsumer.assign(java.util.List.of(topicPartition))
//    var readFrom = newOffset.get() - n
//    if readFrom < 0L then readFrom = 0L
//    nMessageConsumer.seek(topicPartition, readFrom) // we start reading from topic from last read message (offset) minus n
//    val records: ConsumerRecords[String, String] = nMessageConsumer.poll(Duration.ofMillis(250))
//    records.forEach(
//      (r: ConsumerRecord[String, String]) => {
//        val senderUUID = UUID.fromString(r.key())
//        val login = chatUsers.find(_.userId == senderUUID) match
//          case Some(u: User) => u.login
//          case None          => "Deleted User"
//        printMessage(r) //  watch out on offset
//      }
//    )
//    nMessageConsumer.close()  // we close message consumer.


//  @deprecated
//  private def resetOffsetReference(offset: Long): Unit =
//    // val l: long = 3L its not possible to use javas long raw type.j
//    val jk: jLong = jLong.valueOf(offset + 1L)
//    newOffset.set(jk)

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

  private def restartProducer(): Unit =
    chatProducer.close()
    chatProducer = KessengerAdmin.createChatProducer









