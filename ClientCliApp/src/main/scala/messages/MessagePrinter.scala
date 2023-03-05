package io.github.malyszaryczlowiek
package messages

import db.ExternalDB
import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.domain.Domain.{ChatId, Login, MessageTime, Offset, Partition}
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}
import kessengerlibrary.messages.Message
import kessengerlibrary.util.TimeConverter
import kessengerlibrary.status.Status
import kessengerlibrary.status.Status.*

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.time.{LocalDateTime, Duration as jDuration}
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.List as JList
import scala.collection.concurrent.TrieMap
import scala.collection.{immutable, mutable}
import scala.collection.mutable.SortedMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Try, Using}
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
 */
class MessagePrinter(private var me: User, private var chat: Chat, startingOffsets: Map[Int, Long]) :


  /**
   * Should we print messages or only collect them.
   */
  private val printMessage: AtomicBoolean = new AtomicBoolean(false)



  /**
   * Field keeps if we should tracking incomming messages from
   * kafka broker.
   */
  private val continueReading: AtomicBoolean = new AtomicBoolean(true)



  /**
   * TODO implement usage
   */
  private val continueRestarting: AtomicBoolean = new AtomicBoolean(true)



  /**
   * Buffer storing unread messages from kafka broker from specific chat (topic).
   * These messages will be printed if user open this chat.
   */
  private val unreadMessages: TrieMap[MessageTime, (Partition, Offset, Message)] =
    TrieMap.empty[MessageTime, (Partition, Offset, Message)]



  /**
   * This field keeps offset of last read message.
   * TODO usunąć to a offsety trzymać w ParMap w class Chat
   */
  private val offsets: TrieMap[Partition, Offset] = TrieMap.from(startingOffsets)
  // old //  private val newOffset: AtomicLong = new AtomicLong( chat.offset )



  /**
   * This field keeps UTC time of last obtained message.
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
   * Status of MessagePrinter
   */
  private var status: Status = NotInitialized



  /**
   * Method used to starting {@link chatReader}
   * if it not exists or if it is completed
   * (due to some exceptions for example).
   */
  def startMessagePrinter(): Unit =
    if chatReader.isEmpty then
      chatReader = createChatReader()
    else
      if chatReader.get.isCompleted && continueReading.get() then
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
      Using(KessengerAdmin.createChatConsumer(me.userId.toString)) {
        (chatConsumer: KafkaConsumer[String, Message]) =>

          // we create map of topic partitions and offsets
          // of these partitions
          val topicPartitions: Map[TopicPartition, Offset] =
            offsets.map(
              (partition: Partition, offset: Offset) =>
                (new TopicPartition(chat.chatId, partition), offset)
            ).toMap


          // assign partitions of chat topic to kafka consumer
          chatConsumer.assign( CollectionConverters.asJava( topicPartitions.keys.toSeq ) )


          // we manually set offsets to read from topic and
          // we start reading from it from last read message (offset)
          // offset is always set as last read message offset + 1
          // so we dont have duplicated messages.
          topicPartitions.toSeq.foreach(
            (topicPartition, offset) =>
              chatConsumer.seek(topicPartition, offset)
          )


          // before starting loop pulling, we set status to Starting
          status.synchronized {
            status = Starting
          }

          // then we start reading from topic
          while (continueReading.get()) {
            val records: ConsumerRecords[String, Message] = chatConsumer.poll(jDuration.ofMillis(250))

            // if we got some records, we should
            // process them.
            // if we need print messages we do it,
            // otherwise we show notification
            // and collect messages in buffer.
            if ! records.isEmpty then
              if printMessage.get() then printMessage(records)
              else showNotification(records)

//            records.forEach(
//              (record: ConsumerRecord[User, Message]) => {
//                if printMessage.get() then printMessage(record)
//                else showNotification(record)
//
//              }
//            )

            // if everything works fine
            // (we started consumer correctly and has stable connection)
            // we set status to Running
            // status is reassigned every 250 ms (after each while loop).
            status.synchronized {
              status = Running
            }

          } // end of while
      } match {
        case Failure(ex) =>

          // if somethings will fail, we print error message,
          KafkaErrorsHandler.handleWithErrorMessage(ex) match
            case Left(kE: KafkaError) => print(s"${kE.description}. Cannot read messages from server.\n> ")
            case Right(_)             => // not reachable

          status.synchronized {
            status = Error
          }

          // reassign chatReader object for easier restarting
          // chatReader = None

          // and updated db with last stored offset and message time.
          updateDB()

        case Success(_)     =>

          status.synchronized {
            status = Closing
          }

          updateDB()
      }
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
  private def showNotification(records: ConsumerRecords[String, Message]): Unit =
    records.forEach(
      (record: ConsumerRecord[String, Message]) => {

        val sender = record.value().authorId
        if sender != me.userId then
        // we do not need notification of own messages.
          print(s"One new message from ${record.value().authorLogin} in chat '${chat.chatName}'.\n> ")

        val timestamp = record.timestamp()
        val offset    = record.offset()
        val partition = record.partition()
        val message   = record.value()

        // we need to change last message time for sorting purposes
        lastMessageTime.set( timestamp )

        // and actualize chat object
        chat = chat.copy(timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal( timestamp ))

        //  we save each message
        unreadMessages.addOne( timestamp -> (partition, offset, message) )
      }
    )





  /**
   * This method is called when we open chat.
   *
   * This method is threadsafe for unreadMessages. We can transform
   * our TreeMap to SortedMap and then clear unreadMessages and dont worry
   * that between these two calls some item will be added to unreadMessages,
   * because before we call this message in ProgramExecutor
   * we call startPrintingMessages(), so
   * printMessage is set to true and
   * and printMessage() is starting execute in chatReader
   * instead of showNotification(),
   * and so, unreadMessages is not modified in this time.
   */
  def printUnreadMessages(): Unit =


    // todo here wee should synchronize printing unread messages
    //  and not writing new messages to unreadMessages in the same time
//    synchronized(unreadMessages){
//
//    }

    // we sort unread messages according to ascending timestamp


    if unreadMessages.nonEmpty then
      val sorted: immutable.SortedMap[MessageTime, (Partition, Offset, Message)] =
        unreadMessages.to(immutable.SortedMap)

      sorted.foreach( (k: MessageTime, v: (Partition, Offset, Message) ) => {

        // we update offset and last message time
        updateOffsetAndLastMessageTime(v._1, v._2 + 1L, k )

        // we extract and convert required data
        val login = v._3.authorLogin
        val time  = TimeConverter.fromMilliSecondsToLocal( k )

        // and we print each message
        print(s"$login $time >> ${v._3.content}\n> ")
      })

      // clear off unreadMessages
      unreadMessages.clear()

    // else print("> ") // unnecessary usage

    // set to start printing messages
    printMessage.set(true)

    // we update offset and last message time to db
    Future { updateDB() }

    // and, if from some reasons,
    // we do not have chatReader started,
    // or it is completed,
    // we try to restart it now
    startMessagePrinter()

  end printUnreadMessages



  /**
   * This method is called in {@link chatReader}
   * and is called always from external (not main) thread,
   * so we can coll updateDB directly in it.
   */
  private def printMessage(records: ConsumerRecords[String, Message]): Unit =

    // we print all unread messages first
    if unreadMessages.nonEmpty then
      val sorted: immutable.SortedMap[MessageTime, (Partition, Offset, Message)] =
        unreadMessages.to(immutable.SortedMap) // conversion to SortedMap

      sorted.foreach( (k: MessageTime, v: (Partition, Offset, Message) ) => {

        // we update offset and last message time
        updateOffsetAndLastMessageTime(v._1, v._2 + 1L, k )

        // we extract and convert required data
        val login = v._3.authorLogin
        val time  = TimeConverter.fromMilliSecondsToLocal( k )

        // and we print each message
        print(s"$login $time >> ${v._3.content}\n> ")
      })
      unreadMessages.clear()


    // we need to sort messages from records
    // according to rising message time
    val toPrint: mutable.SortedMap[MessageTime, (Partition, Offset, Message)] = mutable.SortedMap.empty


    // we collect all incommed messages
    // and sort them according to increasing
    // message time
    records.forEach(
      (record: ConsumerRecord[String, Message]) => {
        val messageTime = record.timestamp()
        val partition   = record.partition()
        val offset      = record.offset()
        val message     = record.value()
        toPrint.addOne(messageTime -> (partition, offset, message))
      }
    )

    // print all newest messages
    toPrint.foreach(
      (mTime,value) => {
        // map tuple to proper values
        val (part, off, mess): (Partition, Offset, Message) = value

        // print only messages from other users
        if mess.authorId != me.userId then
          val time:    LocalDateTime = TimeConverter.fromMilliSecondsToLocal( mTime )
          val login:   Login         = mess.authorLogin
          val message: String        = mess.content
          print(s"$login $time >> $message\n> ")

        updateOffsetAndLastMessageTime(part, off, mTime)
      }
    )

    // and save this parameters to db
    // note that we are in other thread so we can
    // call this update directly.
    updateDB()

  end printMessage




  /**
   * Method updates last message time and chat offset in db
   * This method is called from external threads always.
   */
  private def updateDB():     Unit = // we update offset and message time for this message in DB
    ExternalDB.updateChatOffsetAndMessageTime(me, chat, offsets.toSeq.sortBy(tuple => tuple._1)) match {
      case Left(queryErrors: QueryErrors) =>
      // queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
      // print(s"Leave the chat, and back in a few minutes.\n> ")
      case Right(value) =>
      // we do not need to notify user (sender)
      // that some values were updated in DB.
    }



  /**
   * In this method we store new offset of chat, and information
   * about last message time. Both parameters we override in chat object
   * as well.
   *
   */
  def updateOffsetAndLastMessageTime(partitionNum: Partition, offset: Offset, newLastMessageTime: MessageTime): Unit =
    offsets.update(partitionNum, offset)
    lastMessageTime.set( newLastMessageTime )
    chat.synchronized{
      chat = chat.copy(
        timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal(newLastMessageTime)
      )
    }



  /**
   * Simple chat getter method.
   * @return {@link chat}
   */
  def getChat: Chat = chat



  /**
   * Simple getter of last message time.
   */
  def getLastMessageTime: Long = lastMessageTime.get()



  /**
   * Returns current status of MessagePrinter.
   */
  def getStatus: Status = status.synchronized { status }



  /**
   * return number of unread messages.
   */
  def getNumOfUnreadMessages: Int =  unreadMessages.size


  /**
   * number of read messages is sum of offsets from
   * all partitions
   * @return
   */
  def getNumOfReadMessages: Long =
    offsets.values.sum


  /**
   * This method should be called
   * when we end working with MessagePrinter
   * (and of course at the end of program execution).
   */
  def closeMessagePrinter(): Unit =

    // we stop restarting thread
    continueRestarting.set(false)

    // we stop main loop in kafka consumer thread
    continueReading.set(false)

    // clear all unread messages
    unreadMessages.clear()

    // and set status to terminated
    status.synchronized { status = Terminated }

    // closing chatReader is unnecessary in this case.
//    chatReader match
//      case Some(future) =>
//        // we wait max 5 seconds
//        if ! future.isCompleted then Try { Await.result(future, Duration.create(5L, SECONDS))  }
//      case None => // we do nothing

    // and finally we save chat offset and last message time to DB
    Future { updateDB() }

  end closeMessagePrinter




  /**
   * Method tries to pull list of chat user from db.
   */
  @deprecated("users list is not used so is not required to pull it from db.")
  private def pullChatUsersList(): Unit =
    Future {
      ExternalDB.findChatAndUsers(me, chat.chatId) match {
        case Left(dbErrors: QueryErrors) =>
          // we print information that we are not able download chat users
          dbErrors.listOfErrors.foreach(queryError => print(s"${queryError.description}. Cannot load chat users.>\n "))
        case Right((_, list: List[User])) =>

        // we reassign user list
        // chatUsers = list

      }
    }


end MessagePrinter // end of class definition



/**
 * MessagePrinter object used to store given
 * how to order MessagePrinter .
 */
object MessagePrinter:

  given messagePrinterReverseOrdering: Ordering[MessagePrinter] with
    override def compare(x: MessagePrinter, y: MessagePrinter): Int =
      if x.getLastMessageTime < y.getLastMessageTime then 1
      else if x.getLastMessageTime > y.getLastMessageTime then -1
      else 0

end MessagePrinter







// we extract valuable informations from consumer record

// check if unread messages is empty or not
//    if unreadMessages.isEmpty then
//
//      // if so we simply print arrived message
//      if sender.login != me.login then print(s"${sender.login} $localTime >> ${message.content}\n> ")
//
//      // and update offset and timestamp
//      updateOffsetAndLastMessageTime( r.offset() + 1L, r.timestamp())
//
//    // if unread messages buffer is not empty
//    else
//
//      // we sort stored messages in offset order
//      val sorted: immutable.SortedMap[Long, Message] =
//        unreadMessages.seq.to(immutable.SortedMap)
//
//      // print all messages from unread message buffer
//      sorted.foreach( (k: Long, v: Message ) => {
//        val login = v.authorLogin
//        val time = TimeConverter.fromMilliSecondsToLocal(v.utcTime)
//        print(s"$login $time >> ${v.content}\n> ")
//      })
//
//      // clear all already printed messages
//      unreadMessages.clear()
//
//      // print current record (message)
//      print(s"${sender.login} $localTime >> ${message.content}\n> ")
//
//      // and update offset and last message parameter
//      updateOffsetAndLastMessageTime(r.offset() + 1L, r.timestamp())
//
//
//    updateDB()
