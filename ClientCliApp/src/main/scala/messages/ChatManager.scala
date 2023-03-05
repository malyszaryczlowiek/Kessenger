package io.github.malyszaryczlowiek
package messages

import account.MyAccount
import db.ExternalDB

import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, Domain, User}
import kessengerlibrary.domain.Chat.orderChatWithDate
import kessengerlibrary.domain.Domain.*
import kessengerlibrary.messages.Message
import kessengerlibrary.kafka.configurators.KafkaConfigurator
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}
import kessengerlibrary.util.TimeConverter
import kessengerlibrary.status.Status
import kessengerlibrary.status.Status.*

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try, Using}
import scala.concurrent.impl.Promise
import concurrent.ExecutionContext.Implicits.global

/**
 * This class is responsible for storing information about user's chats,
 * sending messages in proper chats, sending and receiving invitations
 * to chats.
 */
class ChatManager(var me: User):


  /**
   * Keeps map of user's chats
   */
  private val myChats: TrieMap[ChatId, MessagePrinter] = TrieMap.empty[ChatId, MessagePrinter]



  /**
   * joinProducer object is responsible for sending invitation
   * messages to other users.
   */
  private val joinProducer: KafkaProducer[String, Message] = KessengerAdmin.createJoiningProducer



  /**
   * kafka producer used to send messages to specific chat topic.
   */
  private val chatProducer: KafkaProducer[String, Message] = KessengerAdmin.createChatProducer



  /**
   * AtomicBoolean value keeping information if we should checking
   * our joining topic. This topic collects logs (invitations)
   * send from other users when they invite us to join chat.
   */
  private val continueChecking: AtomicBoolean = new AtomicBoolean(true)



  /**
   * TODO implement usage
   */
  private val continueRestarting: AtomicBoolean = new AtomicBoolean(true)



  /**
   * Controls current offset of joining offset.
   * This value is stored in db, and when we start application
   * value is read in and we can read from topic from specific
   * offset
   *
  */
  private val joinOffset: AtomicLong = new AtomicLong( me.joiningOffset )



  /**
   * Current status of chat manager
   */
  private var status: Status = NotInitialized



  /**
   * This future runs all time during program execution.
   *
   * It handles if someone ask me to join a chat.
   * If someone ask me to join chat I keep this in buffer,
   * And can process in future time.
   * If we do not process buffer and close program,
   * data isn't lost, because offset isn't updated in db,
   * so when we restarting app and
   * we read in all requests (above offset) again.
   */
  private var joiningListener: Option[Future[Unit]] = None



  /**
   * Method tries to create joining topic in kafka broker,
   * and if it does, returns Right(Unit) object.
   * Otherwise returns Left object with specific kafka error.
   * @return
   */
  def tryToCreateJoiningTopic(): Either[KafkaError, Unit] =
    KessengerAdmin.createJoiningTopic(me.userId) match {
      case l @ Left(kafkaError) => l
      case Right(_) =>
        // joining topic created without errors
        // we need to change joining offset because in db is set to -1
        joinOffset.set( 0L )
        Right({})
    }



  /**
   * This method should be called after
   * returning right object by method
   * {@link  tryToCreateJoiningTopic tryToCreateJoiningTopic()}.
   * <p>
   * if {@link  tryToCreateJoiningTopic tryToCreateJoiningTopic()}
   * returns Left object with error other then
   * {@link io.github.malyszaryczlowiek.kessengerlibrary.kafka.errors.KafkaErrorMessage.ChatExistsError KafkaErrorMessage.ChatExistsError}
   * (which means that joining topic already exists),
   * this means that there is some problems with kafka broker,
   * and this method should not be called.
   * This attitude is realized in
   * {@link MyAccount.initialize MyAccount.initialize()} method.
   */
  def startListeningInvitations(): Unit =
    joiningListener match {
      case Some(future) =>

        // when joiningListener completed we assign new one.
        if future.isCompleted && continueChecking.get() then
          joiningListener = assignJoiningListener()

      // if joining listener is not defined. we assign one
      case None => joiningListener = assignJoiningListener()
    }



  /**
   * Method defines listener of incomming joining messages from joining topic.
   *
   * @return
   */
  private def assignJoiningListener(): Option[Future[Unit]] =
    val future: Future[Unit] = Future {

      // at the beginning we create kafka consumer to consume invitation
      // from our joining topic.
      Using(KessengerAdmin.createJoiningConsumer(me.userId.toString)) {
        (joinConsumer: KafkaConsumer[String, Message]) =>

          // we set topic to read from (this topic has only one partition)
          val topic0 = new TopicPartition(Domain.generateJoinId(me.userId), 0)

          // assign this toopic to kafka consumer
          joinConsumer.assign(java.util.List.of(topic0))

          // this may throw IllegalArgumentException if joining topic exists,
          // but in db we do not have updated offset
          // and inserted value of joinOffset (taken from db) is -1.

          // and assign offset for that topic partition
          joinConsumer.seek(topic0, joinOffset.get())

          // before starting loop pulling, we set status to Starting
          status.synchronized {
            status = Starting
          }

          // Start loop to read from topic
          while (continueChecking.get()) {
            val records: ConsumerRecords[String, Message] = joinConsumer.poll(java.time.Duration.ofMillis(1000))

            // for each incoming record we extract key (User) and value (Message)
            // and print notification of chat invitation.
            records.forEach(
              (r: ConsumerRecord[String, Message]) => {
                // key is always null

                // extract data
                val m: Message          = r.value()
                val login               = m.authorLogin
                val groupChat           = m.groupChat
                val time: LocalDateTime = TimeConverter.fromMilliSecondsToLocal( r.timestamp() )

                // set extracted data to chat object
                val chat = Chat(m.chatId, m.chatName, groupChat, time)

                // add chat from invitation to mychats only if
                // we did not do this earlier
                // we need to avoid override MessagePrinter
                // if we created this chat, it already exists in myChats,
                // because was added in sendInvitation() method
                // Note not sure if starting MessagePrinter in external thread will be safe
                if ! myChats.exists(chatAndPrinter => chatAndPrinter._1 == chat.chatId) then
                  myChats.addOne(chat.chatId -> {
                    // we define partition and offsets for new chat
                    val offsets = (0 until KafkaConfigurator.configurator.CHAT_TOPIC_PARTITIONS_NUMBER)
                      .map(i => (i, 0L)).toMap[Partition, Offset]

                    // create MessagePrinter
                    val printer = new MessagePrinter(me, chat, offsets)
                    printer.startMessagePrinter() // start it
                    printer // and save as a value in myChats TrieMap
                  })

                  // prepare and print notification
                  val notification: String = {
                    if groupChat then
                      s"You got invitation from $login to '${chat.chatName}' group chat.\n> "
                    else
                      s"You got invitation from $login to '${chat.chatName}' chat.\n> "
                  }
                  print(notification)


                // we update joining offset
                // offset to save must be always +1L higher than
                // current we read of
                updateOffset(r.offset() + 1L)
              }
            )

            // if everything works fine
            // (we started consumer correctly and has stable connection)
            // we set status to Running
            // status is reassigned every 1000 ms (after each executed while loop).
            status.synchronized {
              status = Running
            }

          }
      } match {
        case Failure(ex) =>
          // if we get error in listener and we should listen further,
          // we should restart it.

          println(s"problem z joiningListener. wywaliło wyjątek ${ex.getMessage}")

          // in first step of restarting we need to close all of our producers
          if joinProducer != null then Future { joinProducer.close() }
          if chatProducer != null then Future { chatProducer.close() }

          // if we lost connection to all kafka brokers
          // we cannot restart immediately again and again and again,
          // because this will keep very busy of our processor
          // Thread.sleep(10_000L)

          if continueChecking.get() then
            KafkaErrorsHandler.handleWithErrorMessage[Unit](ex) match {
              case Left(kError: KafkaError) =>
                print(s"${kError.description}. Connection lost.\n> ") // TODO DELETE for testing purposes

                // we set status to error
                status.synchronized {
                  status = Error
                }
              case Right(_) => status.synchronized {
                status = Error
              } // this will never be called
            }
          else
          // if we do not need read messages from kafka broker,
          // we set status to  Closing
            status.synchronized {
              status = Closing
            }
        case Success(_) =>

          // in first step of restarting we need to close all of our producers
          // if joinProducer != null then joinProducer.close()
          // if chatProducer != null then chatProducer.close()

          //
          status.synchronized {
            status = Closing
          }
      }
    }
    Some(future)



  /**
   * This method adds
   * @param usersChats
   */
  def addChats(usersChats: Map[Chat, Map[Partition, Offset]]): Unit =
    val mapped = usersChats.map(
      (chat: Chat, offsets: Map[Partition, Offset]) =>
        chat.chatId -> {
          val printer = new MessagePrinter(me, chat, offsets)
          printer.startMessagePrinter()
          printer
        })
    myChats.addAll(mapped)



  /**
   *
   */
  def startAllChats(): Unit =
    myChats.foreach(_._2.startMessagePrinter())



  /**
   * This method updates joining offset in db.
   * @param offset
   */
  def updateOffset(offset: Offset): Unit =
    Future { ExternalDB.updateJoiningOffset(me, offset) }
    joinOffset.set(offset)



  /**
   * Returns status of MessagePrinter
   */
  def getStatus: Status = status.synchronized { status }



  /**
   * This method converts myChats map to sorted
   * list of chats.
   * (sorting according to the newest incomming message)
   * @return Sorted list os chats.
   */
  def getUsersChats: List[Chat] =
    myChats.values.map(_.getChat).toList.sorted



  def getMessagePrinter(chat: Chat): Option[MessagePrinter] =
    myChats.get(chat.chatId)



  def getNumOfUnreadMessages(chat: Chat): Int =
    getMessagePrinter(chat) match {
      case Some(mMprinter) => mMprinter.getNumOfUnreadMessages
      case None            => 0 // unrechable
    }


  def getNumOfReadMessages(chat: Chat): Long =
    myChats.get(chat.chatId) match {
      case Some(messagePrinter: MessagePrinter) =>
        messagePrinter.getNumOfReadMessages
      case None => -1 // means no chat
    }


  /**
   * TODO this method is not used yet - use it
   *
   * This method sends invitations to chat,
   * to all selected users and myself.
   *
   * It is good to send to ourself,
   * because in case of db failure, we still have information
   * that we attend in this chat.
   *
   * This may return kafkaError if some user has no created joining topic
   *
   * @param users selected users to add to chat
   * @param chat  chat to join. May exists earlier or be newly created.
   */
  def sendInvitations(chat: Chat, users: List[User]): Either[KafkaError, (Chat, List[User])] =
    val listOfErrorsAndUsers: List[Either[KafkaError, User]] = users.filter(u => u.userId != me.userId )
      .map(u => {
        val joiningTopicName = Domain.generateJoinId(u.userId)
        val recordMetadata: java.util.concurrent.Future[RecordMetadata] =
          val message = Message(
            "",
            me.userId,
            me.login,
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
            chat.chatId,
            chat.chatName,
            chat.groupChat
          )

          // here we define callback of send() method
          val callback: Callback = (metadata: RecordMetadata, ex: Exception) =>
            if ex != null then print(s"Error! Sending invitation to ${u.login} failed.\n> ")
            else print(s"Invitation send to ${u.login}.\n> ")

          // and we send invitation to user's joining topic
          // according to documentation sending is asynchronous,
          // so we do not need do it in separate threads
          joinProducer.send(
            new ProducerRecord[String, Message](joiningTopicName, message), callback
          )

        // RecordMetadata and user are returned tuple object
        (recordMetadata, u)
        }
      )
      .map(
        (recordAndUser: (java.util.concurrent.Future[RecordMetadata], User)) => {
          Try {
            recordAndUser._1.get()  // RecordMetadata
            recordAndUser._2        // User
          } match {
            case Failure(ex)   =>
              KafkaErrorsHandler.handleWithErrorMessage[User](ex)
            case Success(user) =>
              Right(user)
          }
        }
      )

    // we filter off errors
    val errors = listOfErrorsAndUsers.filter {
        case Left(er) => true
        case Right(_) => false
    }

    // we filter off users whom invitation was sent
    val addedUsers = listOfErrorsAndUsers.filterNot {
      case Left(er) => true
      case Right(_) => false
    }

    // if no errors we add created chat to our chat list
    if errors.isEmpty then
      val offsets = (0 until KafkaConfigurator.configurator.CHAT_TOPIC_PARTITIONS_NUMBER)
        .map(i => (i, 0L)).toMap[Partition, Offset]
      addChats(Map(chat -> offsets))
      Right((chat,users))

    // if some errors occurred we check if number of errors
    // is equal to numbers of users
    else
      // we should not worry about someone not get invitation,
      // because this method (sendInvitations()) is called
      // only when invited users are connected to this chat
      // in db. So even when users not get notification,
      // in next log in they will get this new chat in his chat list.

      // if number of errors is not equal of number of users
      // we extract users who did not get invitation
      // and notify sender of this fact
      if errors.size < users.size then
        users.filterNot(u => addedUsers.contains(Right(u)))
          .foreach(u => print(s"Error, Cannot send invitation to ${u.login}.\n> "))

      // if size is equal we print information of first kafka error occured
      // we extract only first error to not bombard user with others errors
      errors.head match {
        // we need reassign to proper new left object
        case Left(kafkaError) => Left(kafkaError)
        case Right(_) => Right((chat, users)) // not reachable because in errors we have only Left
      }



  /**
   * Message is used to sendting messages to specific chat.
   * @param chat    chat where we want to send message
   * @param content content of message
   */
  def sendMessage(chat: Chat, content: String): Unit =
    Future {

      // we create message object to send
      val message = Message(
        content,
        me.userId,
        me.login,
        System.currentTimeMillis(),
        ZoneId.systemDefault(),
        chat.chatId,
        chat.chatName,
        chat.groupChat
      )
      // we send only value, key of record is null
      chatProducer.send(new ProducerRecord[String, Message](chat.chatId, message)) // , callBack)
    }
      // val fut =


      // todo i think we can remove this and only wait for response in consumer

//      // we wait to get response from kafka broker
//      val result = fut.get(5L, TimeUnit.SECONDS)
//
//
//      // extract all needed data, and update myChats with them
//      val newOffset = result.offset() + 1L // to read from this newOffset
//      val partition = result.partition()
//      val messageTime = result.timestamp()
//
//      // ALERT
//      // TODO we cannot update here because we do not know to which partition message was sent
//      myChats.get(chat.chatId) match {
//        case Some(messagePrinter: MessagePrinter) =>
//          messagePrinter.updateOffsetAndLastMessageTime(partition, newOffset, messageTime)
//        case None => // we do not update
//      }
//
//      val updatedChat: Chat = chat.copy(
//        timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal( result.timestamp() )
//      )
//
//      // we update offset and message time for this message in DB
//      ExternalDB.updateChatOffsetAndMessageTime(me, updatedChat, offsets) match {
//        case Left(queryErrors: QueryErrors) =>
//        case Right(value) =>
//      }




  /**
   * In this method we close proper
   * MessagePrinter object and remove it
   * from myChat map.
   * @param chat chat to remove.
   */
  def escapeChat(chat: Chat): Unit =
    myChats.get(chat.chatId) match {
      case Some(messagePrinter: MessagePrinter) =>
        messagePrinter.stopPrintMessages()
        messagePrinter.closeMessagePrinter()
        myChats.remove(chat.chatId)
      case None => {} // rather unreachable
    }



  /**
   * This method is used to closing all connections with
   * kafka broker, irrelevant it is consumer or producer connection.
   */
  def closeChatManager(): Option[KafkaError] =
    Try {

      // we stop restarting thread
      continueRestarting.set(false)

      // switch off main loop fo joining consumer in joining listener
      // it is only place where we set false to continueChecking
      continueChecking.set(false)

      // TODo w razie dalszych problemów należy wywołać je w oddzielnych wątkach

      // close both producers
      if joinProducer != null then Future { joinProducer.close() }
      if chatProducer != null then Future { chatProducer.close() }

      if KessengerAdmin.getStatus == Running then
        Future { KessengerAdmin.closeAdmin() }

      // we close all message printers which are still running
      myChats.values.foreach(_.closeMessagePrinter())

      // we clear our chat list.
      myChats.clear()

      // and set status to terminated
      status.synchronized { status = Terminated }

    } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage(ex) match {
          case Left(kError: KafkaError) => Option(kError)
          case Right(value)             => None // this will never be called
        }
      case Success(_) => None
    }
  end closeChatManager





end ChatManager








/*
 * I set auto.create.topics.enable to false, to not create
 * automatically topics when they not exists, due to this option
 * send() method hangs.
 */





//  this method may be implemented in the future

//  def removeChat(chatToRemove: Chat): Unit =
//    myChats.remove(chatToRemove.chatId) match {
//      case Some((chat, list)) =>
//        print(s"Chat '${chat.chatName}' removed.\n>")
//      case None =>
//        print(s"Error, cannot find chat '${chatToRemove.chatName}'.\n> ")
//    }

