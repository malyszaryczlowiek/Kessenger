package com.github.malyszaryczlowiek
package messages

import account.MyAccount
import db.ExternalDB
import kessengerlibrary.domain.{Chat, Domain, User}
import kessengerlibrary.domain.Domain.*
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}

import com.github.malyszaryczlowiek.kessengerlibrary.db.queries.QueryErrors
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global



/**
 *
 */
class ChatManager(var me: User, private var topicCreated: Boolean = false):

  // producer is thread safe, so we can keep single object of producer.
  private var joinProducer: KafkaProducer[String, String] = KessengerAdmin.createJoiningProducer(me.userId)
  private val chatsToJoin:  ListBuffer[(UserID, ChatId)]  = ListBuffer.empty[(UserID, ChatId)]

  private var transactionInitialized: Boolean = false
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
  private var optionListener: Option[Future[Unit]] = None



  private def assignListener(): Option[Future[Unit]] =
    val future: Future[Unit ] = Future {
      val joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
      val topic = new TopicPartition(Domain.generateJoinId(me.userId), 0)
      joinConsumer.assign(java.util.List.of(topic))
      joinConsumer.seek(topic, joinOffset.get() )
      // this may throw IllegalArgumentException if joining topic exists,
      // but in db we do not have updated offset
      // and inserted value of joinOffset (taken from db) is -1.
      while ( continueChecking.get() ) {
        val records: ConsumerRecords[String, String] = joinConsumer.poll(java.time.Duration.ofMillis(1000))
        records.forEach(
          (r: ConsumerRecord[String, String]) => {
            val userID = UUID.fromString(r.key())
            val chatId = r.value()
            chatsToJoin.addOne((userID, chatId))
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
                    MyAccount.addChat(chat, users)
                    updateUsersOffset( r.offset() + 1L )
                }
              case Left(_) => () // if errors, do nothing, because I am added in users_chats
              // so in next logging chat will show up.
            }
          }
        )
      }
      joinConsumer.close()
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
              optionListener = assignListener()
            else
              // if we should not listen further we reassign to None
              error = None
              optionListener = None
          case Success(_) =>
            // if we closed this future successfully, we simply reassign to None
            error = None
            optionListener = None
        }
      }
    )
    Some(future)




  /**
   *
   * @return
   */
  private def startChatManager(): Unit =
    if topicCreated then
      optionListener = assignListener()
    else
      KessengerAdmin.createJoiningTopic(me.userId) match {
        case Left(kafkaError) =>
          error = Some(kafkaError)
        case Right(_) => // joining topic created without errors
          topicCreated = true
          updateUsersOffset(0L)
          optionListener = assignListener()
      }

  // here we start ChatManager
  startChatManager()



  private def updateUsersOffset(offset: Long): Unit =
    joinOffset.set( offset )
    me = me.copy(joiningOffset = joinOffset.get() ) // we need to change joining offset because in db is set to -1
    MyAccount.updateUser(me)
    ExternalDB.updateJoiningOffset(me, offset) match {
      case Right(user) => // println(s"User's data updated. ")
      case Left(dbError: QueryErrors) => // println(s"Cannot update user's data to DB. ") // joining offset: ${dbError.listOfErrors.head.description}
    }





  def getError(): Option[KafkaError] =
    errorLock.synchronized( {
      val toReturn = error
      error = None
      toReturn
    } )





  /**
   * This method sends invitations to chat,
   * to all selected users.
   *
   * This may return kafkaError if some user has no created joining topic
   *
   * @param users selected users to add to chat
   * @param chat  chat to join. May exists earlier or be newly created.
   */
  def askToJoinChat(users: List[User], chat: Chat): Either[KafkaError, Chat] =
    Try {
      if transactionInitialized then
        sendInvitations(users, chat)
      else
        // before each transaction join producer must be
        // initialized exactly one time.
        joinProducer.initTransactions()
        sendInvitations(users, chat)
        transactionInitialized = true
    } match {
      case Failure(ex) =>
        restartProducer()
        KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
      case Success(_)  => Right(chat)
    }



  /**
   * In this method w send joining to chat information to
   * users, but we must do it in different thread, because of
   * blocking nature of send() method in case of sending
   * to non existing topic.
   *
   * I set auto.create.topics.enable to false, to not create
   * automatically topics when they not exists, due to this option
   * send() method hangs.
   *
   * But, even if here we cannot send invitations to some set of users,
   * (because of for example  not created joining topic), is not a problem,
   * because of users have information about new chat in DB.
   * So when they log in again, they will get information from DB obout new chat.
   *
   *
   */
  private def sendInvitations(users: List[User], chat: Chat): Unit =
    // we start future and forget it.
    Future {
      joinProducer.beginTransaction()
      users.foreach(u => {
        if u.userId != me.userId then
          val joiningTopicName = Domain.generateJoinId(u.userId)
          joinProducer.send(new ProducerRecord[String, String](joiningTopicName, me.userId.toString, chat.chatId))
          print(s"Invitation send to ${u.login}.\n> ")
      })
      joinProducer.commitTransaction()
    }
    MyAccount.addChat(chat, users)



  /**
   * According to documentation some Exceptions like
   * ProducerFencedException, OutOfOrderSequenceException,
   * UnsupportedVersionException, or an AuthorizationException,
   * requires aborting transaction,
   * and creating another producer object.
   */
  private def restartProducer(): Unit =
    joinProducer.abortTransaction()
    joinProducer.close()
    transactionInitialized = false
    joinProducer = KessengerAdmin.createJoiningProducer(me.userId)



  /**
   *
   */
  def closeChatManager(): Option[KafkaError] =
    Try {
      joinProducer.close()
      continueChecking.set(false)
      if optionListener.isDefined then
        optionListener.get.onComplete {
          // TODO delete, used in integration tests
          case Failure(exception) =>
            println(s"join consumer closed with Error.")
          case Success(unitValue) => // we do nothing
            // println(s"option listener w Chat Manager zamkni??ty normalnie. ")  // TODODELETE
        }
    } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage(ex) match {
          case Left(kError: KafkaError) => Option(kError)
          case Right(value)             => None // this will never be called
        }
      case Success(_) => None
    }



  def updateOffset(offset: Long): Unit = joinOffset.set(offset)

  def setTopicCreated(boolean: Boolean): Unit = topicCreated = boolean







