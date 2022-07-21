package com.github.malyszaryczlowiek
package messages

import db.ExternalDB
import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.domain.Domain.{ChatId, Login}
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}
import kessengerlibrary.messages.Message
import kessengerlibrary.util.TimeConverter

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.time.{Duration, LocalDateTime}
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.List as JList

import scala.collection.immutable
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Using}
import concurrent.ExecutionContext.Implicits.global


/**
 * MessagePrinter class is designed to collect messages from specific
 * kafka topic, and to print them when user opens chat.
 *
 * Second target of class is database updates of offset of
 *
 *
 * @param me {@link User} object who reads from chat.
 * @param chat chat we print messages from
 * @param chatUsers list of all users of chat
 */
class MessagePrinter(private var me: User, private var chat: Chat, private var chatUsers: List[User]) :


  /**
   * Should we print messages or only collect them.
   */
  private val printMessage: AtomicBoolean = new AtomicBoolean(false)



  /**
   * Parameter keeps if we should tracking incomming messages from
   * kafka broker.
   */
  private val continueReading: AtomicBoolean = new AtomicBoolean(true)



  /**
   * Buffer storing unread messages from kafka broker from specific chat (topic).
   * This messages will be printed if user open this chat.
   */
  private val unreadMessages: ParTrieMap[Long, (User,Message)] =
    ParTrieMap.empty[Long, (User, Message)]



  /**
   * This parameter keeps offset of last read message.
   */
  private val newOffset: AtomicLong = new AtomicLong( chat.offset )



  /**
   * This parameter keeps UTC time of last obtained message.
   * This value is used to sort list of chats,
   * from newest to oldest chats.
   */
  private val lastMessageTime: AtomicLong = new AtomicLong( TimeConverter.fromLocalToEpochTime(chat.timeOfLastMessage) )



  /**
   * THis Future object is responsible of collecting messages from
   * kafka broker from specific chat topic.
   * @see {@link createChatReader createChatReader()} method implementation.
   */
  private var chatReader: Option[Future[Unit]] = None



  /**
   * If from some reasons, we do not have list of
   * chat users, we retry to get it.
   * This instruction is called in constructor.
   */
  if chatUsers.isEmpty then
    pullChatUsersList()


  /**
   * Method tries to pull list of chat user from db.
   */
  private def pullChatUsersList(): Unit =
    Future {
      ExternalDB.findChatAndUsers(me, chat.chatId) match {
        case Left(dbErrors: QueryErrors) =>
          // we print information that we are not able download chat users
          dbErrors.listOfErrors.foreach(queryError => print(s"${queryError.description}. Cannot load chat users.>\n "))
        case Right((_, list: List[User])) =>

          // we reassign user list
          chatUsers = list

          // when list of users is reassigned
          // we can try to start chatReader
          chatReader = createChatReader()
      }
    }


  /**
   * Method used to starting {@link chatReader}
   * if it not exists or if it is completed
   * (due to some exceptions for example).
   */
  def startMessagePrinter(): Unit =
    if chatReader.isEmpty then
      chatReader = createChatReader()
    else
      if ! chatReader.get.isCompleted then
        chatReader = createChatReader()



  /**
   * Method defines creates {@link chatReader} object.
   * Internally it uses kafka consumer.
   * @return
   */
  private def createChatReader(): Option[Future[Unit]] =
    val future: Future[Unit] = Future {

      // we create consumer object,
      // this consumer is going to read from topic of this chat,
      // starting from saved chat offset parameter.
      // note that kafka consumer is closed automatically in
      // this case by Using clause.
      Using( KessengerAdmin.createChatConsumer(me.userId.toString) ) {
        (chatConsumer: KafkaConsumer[User, Message]) =>
          val topicPartition0: TopicPartition = new TopicPartition(chat.chatId, 0)

          // assign specific topic and partition to read from
          chatConsumer.assign(JList.of(topicPartition0))

          // we manually set offset to read from and
          // we start reading from topic from last read message (offset)
          chatConsumer.seek(topicPartition0, newOffset.get() )

          // then we start reading from topic
          while (continueReading.get()) {
            val records: ConsumerRecords[User, Message] = chatConsumer.poll(Duration.ofMillis(250))
            records.forEach(
              (record: ConsumerRecord[User, Message]) => {

                // if we need print messages we do it,
                // otherwise we show notification
                // and collect messages in buffer.
                if printMessage.get() then printMessage( record )
                else showNotification( record )

              }
            )
          }
      }
    }
    future.onComplete {
      case Failure(ex) =>

        // if somethings will fail, we print error message,
        KafkaErrorsHandler.handleWithErrorMessage(ex) match
          case Left(kE: KafkaError) => print(s"${kE.description}. Cannot read messages from server.\n> ")
          case Right(_)             => // not reachable

        // reassign chatReader object
        chatReader = None

        // and updated db with last stored offset and message time.
        updateDB()

      case Success(_) => updateDB()
      // after work we should save new offset and time
    }
    Some(future)

  end createChatReader



  /**
   * This method only stops printing messages,
   * but does not stop Kafka consumer so
   * messages are still collecting in {@link unreadMessages }.
   */
  def stopPrintMessages(): Unit =
    printMessage.set(false)
    Future { updateDB() }



  /**
   * Method shows notification of incoming messages
   * and collects these messages in {@link unreadMessages}
   * buffer. <p>
   *
   * @note Notifications do not change offset,
   *       but change {@link lastMessageTime },
   *       which is used to sorting all chats in
   *       descending order of last message time.
   */
  private def showNotification(r: ConsumerRecord[User, Message]): Unit =
    val sender = r.key().login
    if sender != me.login then

      // we do not need notification of own messages.
      print(s"One new message from $sender in chat '${chat.chatName}'.\n> ") // print notification

      // we need to change last message time for sorting purposes
      lastMessageTime.set( r.timestamp() )

      // and actualize chat object
      chat = chat.copy(timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal( r.timestamp() ))

      // finally we save last message
      unreadMessages.addOne(r.offset() -> ( r.key(), r.value() ))



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

    // we sort unread messages according to ascending offset
    val sorted: immutable.SortedMap[Long, (User, Message)] =
      unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap

    if sorted.nonEmpty then
      sorted.foreach( (k: Long, v: (User, Message)) => {

        // we update offset and last message time
        updateOffsetAndLastMessageTime( k + 1L, v._2.utcTime )

        // we extract and convert required data
        val login = v._1.login
        val time = TimeConverter.fromMilliSecondsToLocal(v._2.utcTime)

        // and we print each message
        print(s"$login $time >> ${v._2.content}\n> ")
      })
    else print("> ")

    // clear off unreadMessages
    unreadMessages.clear()

    // set to start printing messages
    printMessage.set(true)

    // we update offset and last message time to db
    Future { updateDB() }

    // and if from some reasons we do not have chatReader started,
    // or is it is completed,
    // we try to restart it now
    startMessagePrinter()

  end printUnreadMessages



  /**
   * This method is called in {@link chatReader}
   * and is called always from external thread (not main),
   * so we can coll updateDB directly in it.
   */
  private def printMessage(r: ConsumerRecord[User, Message]): Unit =
    val localTime: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )
    val sender: User = r.key()
    val message = r.value()
    if unreadMessages.isEmpty then
      if sender.login != me.login then print(s"${sender.login} $localTime >> ${message.content}\n> ")
      updateOffsetAndLastMessageTime(r.offset() + 1L, r.timestamp())
    else
      val sorted: immutable.SortedMap[Long, (User, Message)] =
        unreadMessages.seq.to(immutable.SortedMap) // conversion to SortedMap
      sorted.foreach( (k: Long, v: (User, Message)) => {
        // this prints messages to user
        val login = v._1.login
        val time = TimeConverter.fromMilliSecondsToLocal(v._2.utcTime)
        print(s"$login $time >> ${v._2.content}\n> ")
        // updateOffsetAndLastMessageTime(r.offset() + 1L, r.timestamp())
      } )
      unreadMessages.clear() // clear off ParSequence
      print(s"${sender.login} $localTime >> ${message.content}\n> ") // and finally print last message.
      updateOffsetAndLastMessageTime(r.offset() + 1L, r.timestamp())
    updateDB()


  private def updateDB():     Unit = // we update offset and message time for this message in DB
    ExternalDB.updateChatOffsetAndMessageTime(me, Seq(chat)) match {
      case Left(queryErrors: QueryErrors) =>
      // queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
      // print(s"Leave the chat, and back in a few minutes.\n> ")
      case Right(value) =>
      // we do not need to notify user (sender)
      // that some values were updated in DB.
    }


  def updateOffsetAndLastMessageTime(offset: Long, newLastMessageTime: Long): Unit =
    newOffset.set( offset )
    lastMessageTime.set( newLastMessageTime )
    chat = chat.copy(
      offset = offset,
      timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal(newLastMessageTime)
    )


  /**
   * Simple chat getter method.
   * @return {@link chat}
   */
  def getChat: Chat = chat


  /**
   * Method
   * @return
   */
  def getLastMessageTime: Long = lastMessageTime.get()


  def closeMessagePrinter(): Unit =
    continueReading.set(false)


end MessagePrinter


/**
 * TODO write tests for fake chats with empty
 *  user lists.
 */
object MessagePrinter:

  given messagePrinterOrdering: Ordering[MessagePrinter] with
    override def compare(x: MessagePrinter, y: MessagePrinter): Int =
      if x.getLastMessageTime > y.getLastMessageTime then 1
      else if x.getLastMessageTime < y.getLastMessageTime then -1
      else 0

  given invertedMessagePrinterOrdering(using normal: Ordering[MessagePrinter]): Ordering[MessagePrinter] with
    override def compare(x: MessagePrinter, y: MessagePrinter): Int =
      -1 * normal.compare(x, y)
//
//  given orderingMessagePrinter: Ordering[MessagePrinter]
