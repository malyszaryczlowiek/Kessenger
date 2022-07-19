package com.github.malyszaryczlowiek
package messages

import db.ExternalDB
import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.messages.Message
import kessengerlibrary.util.TimeConverter
import kessengerlibrary.domain.Domain.Login

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.time.{Duration, LocalDateTime}
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.collection.immutable
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


class MessagePrinter(me: User, var chat: Chat, chatUsers: List[User]) {

  // reading chats

  private val printMessage:     AtomicBoolean = new AtomicBoolean(false)
  private val continueReading:  AtomicBoolean = new AtomicBoolean(true)
  private val unreadMessages: ParTrieMap[Long, (User,Message)] =
    ParTrieMap.empty[Long, (User, Message)]
  private val newOffset:           AtomicLong = new AtomicLong( chat.offset )
  private val lastMessageTime:     AtomicLong = new AtomicLong( TimeConverter.fromLocalToEpochTime(chat.timeOfLastMessage) )

  // todo to trzeba startować później
  private var chatReader: Option[Future[Unit]] = None // Some( createChatReader() )

  private def createChatReader(): Future[Unit] =
    val future = Future {
      val chatConsumer: KafkaConsumer[User, Message] = KessengerAdmin.createChatConsumer(me.userId.toString)



      val topicPartition0: TopicPartition                = new TopicPartition(chat.chatId, 0)

      chatConsumer.subscribe(java.util.List.of(chat.chatId))
      // assign specific topic to read from
      // chatConsumer.assign(java.util.List.of(topicPartition0))
      // we manually set offset to read from and
      // we start reading from topic from last read message (offset)
      //chatConsumer.seek(topicPartition0, newOffset.get() )  // TODO
      //      chatConsumer.seek(topicPartition1, newOffset.get() )  // TODO
      //      chatConsumer.seek(topicPartition2, newOffset.get() )  // TODO

      while (continueReading.get()) {
        val records: ConsumerRecords[User, Message] = chatConsumer.poll(Duration.ofMillis(250))
        records.forEach(
          (record: ConsumerRecord[User, Message]) => {
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
  private def showNotification(r: ConsumerRecord[User, Message]): Unit =
    val sender = r.key().login
    if sender != me.login then
      print(s"One new message from $sender in chat '${chat.chatName}'.\n> ") // print notification
      unreadMessages.addOne(r.offset() -> ( r.key(), r.value() ))
      // val time: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )
      // lastUnreadOffset.set( r.offset() + 1L )



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
    val sorted: immutable.SortedMap[Long, (User, Message)] =
      unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
    if sorted.nonEmpty then
      sorted.foreach( (k: Long, v: (User, Message)) => {
        newOffset.set( k + 1L )
        lastMessageTime.set( v._2.utcTime )
        // this prints messages to user
        val login = v._1.login
        val time = TimeConverter.fromMilliSecondsToLocal(v._2.utcTime)
        print(s"$login $time >> ${v._2.content}\n> ")
      } )
    else print("> ")
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
  private def printMessage(r: ConsumerRecord[User, Message]): Unit =
    val localTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )
    val sender: User = r.key()
    val message = r.value()
    if unreadMessages.isEmpty then
      if sender.login != me.login then print(s"${sender.login} $localTime >> ${message.content}\n> ")
      newOffset.set( r.offset() + 1L )
      lastMessageTime.set( r.timestamp() )
    else
      val sorted: immutable.SortedMap[Long, (User, Message)] =
        unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
      sorted.foreach( (k: Long, v: (User, Message)) => {
        newOffset.set( k + 1L )
        lastMessageTime.set( v._2.utcTime )
        // this prints messages to user
        val login = v._1.login
        val time = TimeConverter.fromMilliSecondsToLocal(v._2.utcTime)
        print(s"$login $time >> ${v._2.content}\n> ")
      } )
      unreadMessages.clear() // clear off ParSequence
      print(s"${sender.login} $localTime >> ${message.content}\n> ") // and finally print last message.
      newOffset.set( r.offset() + 1L )
      lastMessageTime.set( r.timestamp() )
    updateDB()


  private def updateDB():     Unit = // we update offset and message time for this message in DB
    ExternalDB.updateChatOffsetAndMessageTime(me, Seq(updatedChat)) match {
      case Left(queryErrors: QueryErrors) =>
      // queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
      // print(s"Leave the chat, and back in a few minutes.\n> ")
      case Right(value) =>
      // we do not need to notify user (sender)
      // that some values were updated in DB.
    }


}

