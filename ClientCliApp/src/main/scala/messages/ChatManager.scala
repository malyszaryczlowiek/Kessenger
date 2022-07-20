package com.github.malyszaryczlowiek
package messages

import account.MyAccount
import db.ExternalDB
import kessengerlibrary.domain.{Chat, Domain, User}
import kessengerlibrary.domain.Domain.*
import kessengerlibrary.messages.Message
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}
import kessengerlibrary.db.queries.QueryErrors

import com.github.malyszaryczlowiek.kessengerlibrary.util.TimeConverter
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.mutable.ParTrieMap
import scala.collection.parallel.mutable.ParSeq
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try, Using}
import scala.concurrent.impl.Promise
import concurrent.ExecutionContext.Implicits.global
import scala.collection.parallel.ParIterable

/**
 *
 */
class ChatManager(var me: User):

  private val myChats: ParTrieMap[ChatId, MessagePrinter] = ParTrieMap.empty[ChatId, MessagePrinter]

  //private val chatsToJoin: ParTrieMap[Chat, User] = ParTrieMap.empty[Chat, User]


  // producer is thread safe, so we can keep single object of producer.
  private val joinProducer: KafkaProducer[User, Message] = KessengerAdmin.createJoiningProducer()
  // private val chatsToJoin:  ListBuffer[(User, Chat)]  = ListBuffer.empty[(User, Chat)]

  // private var transactionInitialized: Boolean = false
  private val continueChecking: AtomicBoolean = new AtomicBoolean(true)


  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  private val joinOffset: AtomicLong = new AtomicLong( me.joiningOffset )

  private val errorLock: AnyRef = new AnyRef

  private var error: Option[KafkaError] = None

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

  private val restartingThread: Option[Future[Unit]] = None


  def tryToCreateJoiningTopic(): Either[KafkaError, Unit] =
    KessengerAdmin.createJoiningTopic(me.userId) match {
      case l @ Left(kafkaError) => l
      case Right(_) => // joining topic created without errors
        joinOffset.set( 0L ) // we need to change joining offset because in db is set to -1
        Right({})
    }


  def addChats(usersChats: Map[Chat, List[User]]): Unit =
    val mapped = usersChats.map(
      (chat: Chat, list: List[User]) => chat.chatId -> new MessagePrinter(me, chat, list))
    myChats.addAll(mapped)



  def startListeningInvitations(): Unit =
//    restartingThread match {
//      case Some(value) => ???
//      case None =>
//        restartingThread = Future {
//
//        }
//    }

    joiningListener match {
      case Some(_) => // if already started we do nothing
      case None    => joiningListener = assignJoiningListener()
    }



  def updateOffset(offset: Long): Unit =
    Future { ExternalDB.updateJoiningOffset(me, offset) }
    joinOffset.set(offset)





  private def assignJoiningListener(): Option[Future[Unit]] =
    val future: Future[Unit] = Future {
      Using (KessengerAdmin.createJoiningConsumer(me.userId.toString)) {
        (joinConsumer: KafkaConsumer[User, Message]) =>
          val topic0 = new TopicPartition(Domain.generateJoinId(me.userId), 0)
          joinConsumer.assign(java.util.List.of(topic0))
          // this may throw IllegalArgumentException if joining topic exists,
          // but in db we do not have updated offset
          // and inserted value of joinOffset (taken from db) is -1.
          joinConsumer.seek(topic0, joinOffset.get() )
          while ( continueChecking.get() ) {
            val records: ConsumerRecords[User, Message] = joinConsumer.poll(java.time.Duration.ofMillis(1000))
            records.forEach(
              (r: ConsumerRecord[User, Message]) => {
                val user = r.key()
                val m: Message = r.value()
                val groupChat = false // TODO PILNE
                //todo  ZAIMPLEMENTOWAĆ że MESSAGE PRZENOSI TEŻ INFO czy jest to  GROUP CHAT
                val chat = Chat(m.chatId, m.chatName, groupChat, 0L, LocalDateTime.MIN)
                myChats.addOne(chat.chatId -> new MessagePrinter(me, chat, List.empty[User]))
                updateOffset(r.offset() + 1L)
                val group = "group "  // todo test it
                print(s"You got invitation from ${user.login} to ${if groupChat then group}chat ${chat.chatName}.\n> ")
              }
            )
          }
      }
    }
    future.onComplete(
      (tryy: Try[Unit]) => {
        tryy match {
          case Failure(ex) =>
            // if we get error in listener and we should listen further,
            // we should restart it.
            if continueChecking.get() then
              errorLock.synchronized( {
                KafkaErrorsHandler.handleWithErrorMessage[Unit](ex) match {
                  case Left(kError: KafkaError) => error = Some(kError)
                  case Right(_)                 => error = None // this will never be called
                }
              } )
              // we try to restart
              println(s"chat manager restarted.") // TODO DELETE
              // joiningListener = assignJoiningListener() // TODO i switch off restarting
              joiningListener = None
            else
              // if we should not listen further we reassign to None
              println(s"chat manager listener reassigned to none. ") // TODO DELETE
              error = None
              joiningListener = None
          case Success(_) =>
            // if we closed this future successfully, we simply reassign to None
            error = None
            joiningListener = None
        }
      }
    )
    Some(future)

/*
                ExternalDB.findChatAndUsers(me, chatId) match {
                  case Right((chat: Chat, users: List[User])) =>
                    MyAccount.getMyChats.find(chatAndExecutor => chatAndExecutor._1.chatId == chat.chatId) match {
                      // if chat already exists in my chat list, do nothing, we must not add this chat to list of my chats
                      case Some(_) => ()
                      case None =>
                        users.find(_.userId == userID) match {
                          case Some(user) => print(s"You got invitation from ${user.login} to chat ${chat.chatName}.\n> ")
                          case None       => print(s"Warning!?! Inviting user not found???\n> ")
                        }

                        updateUsersOffset(  )
                    }
                  case Left(_) => () // if errors, do nothing, because I added in users_chats
                  // so in next logging chat will show up.
                }

*/


/*
// todo przepisać to do KafkaKonfiguratora w Kessenger library
  * I set auto.create.topics.enable to false, to not create
   * automatically topics when they not exists, due to this option
   * send() method hangs.
   *
// TODO to jest skonfigurowane w docker-compose.
*/






  def getError: Option[KafkaError] =
    errorLock.synchronized( {
      val toReturn = error
      error = None
      toReturn
    } )





  /**
   * This method sends invitations to chat,
   * to all selected users and myself.
   *
   * This may return kafkaError if some user has no created joining topic
   *
   * @param users selected users to add to chat
   * @param chat  chat to join. May exists earlier or be newly created.
   */
  def askToJoinChat(users: List[User], chat: Chat): Either[KafkaError, Chat] =
    val listOfErrors: List[Either[KafkaError, Chat]] = users.filter(u => u.userId != me.userId )
      .map(u => {
        val joiningTopicName = Domain.generateJoinId(u.userId)
        val recordMetadata: java.util.concurrent.Future[RecordMetadata] =
          val message = Message(
            "",
            me.userId,
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
            chat.chatId,
            chat.chatName
          )
          // TODO tutaj przeanalizować to wysyłanie.
          joinProducer.send(
            new ProducerRecord[User, Message](joiningTopicName, me, message),
            (metadata: RecordMetadata, ex: Exception) =>
              if ex != null then print(s"Error sending invitation to ${u.login}.\n> ")
              else print(s"Invitation send to ${u.login}.\n> ")
          )
        recordMetadata
        }
      )
      .map(
        (recordMetadata: java.util.concurrent.Future[RecordMetadata]) =>
          Try {
            recordMetadata.get()
            chat
          } match {
            case Failure(ex)   =>
              KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
            case Success(chat) =>
              Right(chat)
          }
      )
      .filter( either =>
        either match {
          case Left(er) => true
          case Right(_) => false
        }
      )
    if listOfErrors.isEmpty then
      // myChats.addOne() // todo dodać jeszcze czat
      Right(chat)
    else
      listOfErrors.head





  /**
   *
   */
  def closeChatManager(): Option[KafkaError] =
    Try {
      // TODO Tutaj upchnąć wszystkie rzeczy, które należy pozamykać.
      joinProducer.close()
      continueChecking.set(false)
      if joiningListener.isDefined then
        joiningListener.get.onComplete {
          // TODO delete, used for tests
          case Failure(exception) =>
            println(s"join consumer closed with Error.")
          case Success(unitValue) => // we do nothing
          // println(s"option listener w Chat Manager zamknięty normalnie. ")  // TODODELETE
        }
    } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage(ex) match {
          case Left(kError: KafkaError) => Option(kError)
          case Right(value)             => None // this will never be called
        }
      case Success(_) => None
    }












  /*
   *  Methods addopted from deprecated MyAccount object
  */
  // TODo this method may be implemented in the future
//  def removeChat(chatToRemove: Chat): Unit =
//    myChats.remove(chatToRemove.chatId) match {
//      case Some((chat, list)) =>
//        print(s"Chat '${chat.chatName}' removed.\n>")
//      case None =>
//        print(s"Error, cannot find chat '${chatToRemove.chatName}'.\n> ")
//    }


  //    myChats.find( (chatId: ChatId, _) => chatId == chatToRemove.chatId) match {
//      case None               =>
//        print(s"Cannot remove chat '${chatToRemove.chatName}'. Does not exist in chat list.\n> ")
//      case Some((found, _)) =>
//        myChats.remove(found)
//        print(s"Chat '${found.chatName}' removed from list.\n> ")
//    }


  def getMyObject: User = me







  //*****************************
  // Sending new messages
  //*****************************



  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  private val chatProducer:   KafkaProducer[User, Message] = KessengerAdmin.createChatProducer

  // (offset, (login, date, message string))
  // private val unreadMessages: ParTrieMap[Long,(Login, LocalDateTime, String)] = ParTrieMap.empty[Long, (Login, LocalDateTime, String)]



  def sendMessage(chat: Chat, content: String): Unit =
    Future {

      // we create message object to send
      val message = Message(
        content,
        me.userId,
        System.currentTimeMillis(),
        ZoneId.systemDefault(),
        chat.chatId,
        chat.chatName
      )
      val fut = chatProducer.send(new ProducerRecord[User, Message](chat.chatId, me, message)) // , callBack)


      // we wait to get response from kafka broker
      val result = fut.get(5L, TimeUnit.SECONDS)


      // extract all needed data, and update myChats with them
      val newOffset = result.offset() + 1L // to read from this newOffset

      myChats.get(chat.chatId) match {
        case Some(messagePrinter: MessagePrinter) => // myChats.update(chat.chatId, (updatedChat, chatAndList._2))
          messagePrinter.updateOffsetAndLastMessageTime(newOffset, result.timestamp())
        case None => // we do not update
      }

      val updatedChat: Chat = chat.copy(
        offset = newOffset,
        timeOfLastMessage = TimeConverter.fromMilliSecondsToLocal( result.timestamp() )
      )

      // we update offset and message time for this message in DB
      ExternalDB.updateChatOffsetAndMessageTime(me, Seq(updatedChat)) match {
        case Left(queryErrors: QueryErrors) =>
        // queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
        // print(s"Leave the chat, and back in a few minutes.\n> ")
        case Right(value) =>
        // we do not need to notify user (sender)
        // that some values were updated in DB.
      }
    }


  /**
   * Method closes producer, closes consumer loop in another thread,
   * with closing that thread and closes consumer object.
   *
   * @return returns chat object with updated offset,
   *         which must be saved to DB subsequently.
   */
  def closeChats(): Unit =
    chatProducer.close()
    joinProducer.close()






























//    Try {
//      if transactionInitialized then
//        println(s"TRANSACTION NOT INITIALIZED") // TODO DELETE
//        sendInvitations(users, chat)
//      else
//        // before each transaction join producer must be
//        // initialized exactly one time.
//        println(s"BEFORE TRANSACTION INITIALIZATION")// TODO DELETE
//        // joinProducer.initTransactions()
//        println(s"TRANSACTION INITIALIZED")// TODO DELETE
//        sendInvitations(users, chat)
//        transactionInitialized = true
//    } match {
//      case Failure(ex) =>
//        restartProducer()
//        KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
//      case Success(_)  => Right(chat)
//    }



  /**
   * In this method w send joining to chat information to
   * users, but we must do it in different thread, because of
   * blocking nature of send() method in case of sending
   * to non existing topic.
   *

   * But, even if here we cannot send invitations to some set of users,
   * (because of for example  not created joining topic), is not a problem,
   * because of users have information about new chat in DB.
   * So when they log in again, they will get information from DB obout new chat.
   *
   *
   */
//  @deprecated
//  private def sendInvitations(users: List[User], chat: Chat): Unit =
//    // we start future and forget it.
//    Future {
//      joinProducer.beginTransaction()
//
//      joinProducer.commitTransaction()
//    }
//    MyAccount.addChat(chat, users)

