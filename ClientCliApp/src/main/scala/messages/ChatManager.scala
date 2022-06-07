package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.account.MyAccount
import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.QueryErrors
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.*
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorStatus, KafkaErrorsHandler}

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global


/**
 *
 * @param me
 * @param topicCreated
 */
class ChatManager(var me: User, private var topicCreated: Boolean = false):

  private var joinProducer: KafkaProducer[String, String] = KessengerAdmin.createJoiningProducer(me.userId)

  private val chatsToJoin:  ListBuffer[(UserID, ChatId)]  = ListBuffer.empty[(UserID, ChatId)]

  private var transactionInitialized: Boolean = false
  val continueChecking: AtomicBoolean = new AtomicBoolean(true)


  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  var joinOffset: Long = me.joiningOffset


  /**
   * This future runs all time during program execution.
   *
   * It handles if someone ask me to join a chat.
   * If someone ask us to join chat w keep this in buffer,
   * And can process in future time.
   * If we do not process buffer and close program,
   * data isn't lost, because offset isnt updated in db,
   * so when we restarting app and
   * we read in all requests (above offset) again.
   */
  private var optionListener: Option[Future[Unit]] = None



  /**
   *
   * @return
   */
  def startListening(): Option[KafkaError] =
    if topicCreated then tryStartAndHandleError()
    else
      KessengerAdmin.createJoiningTopic(me.userId) match {
        case Left(kafkaError) => Some(kafkaError)
        case Right(_) => // joining topic created without errors
          topicCreated = true
          joinOffset = 0L
          me = me.copy(joiningOffset = joinOffset) // we need to change joining offset because in db is set to -1
          ExternalDB.updateJoiningOffset(me, joinOffset) match {
            case Right(user) =>
              println(s"User's data updated. ")
            case Left(dbError: QueryErrors) =>
              println(s"Cannot update user's data to DB. ") // joining offset: ${dbError.listOfErrors.head.description}
          }
          MyAccount.updateUser(me)
          tryStartAndHandleError()
      }



  /**
   *
   * @return
   */
  private def tryStartAndHandleError(): Option[KafkaError] =
    Try { startListener() } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage(ex) match {
          case Left(kafkaError) =>
            println(s"ERROR error is in ChatManager.tryStartAndHandleError().") // TODO delete it
            Some (kafkaError)
          case Right(_)         => None // this will never be called
        }
      case Success(_) => None
    }



  /**
   * In this method
   * We set our joining consumer to read from our
   * joining topic starting from our last read offset.
   * that even if we find in topic invitation to chat,
   * which is currently written in in DB, This invitation
   * will not show up twice (See future value below).
   * Note
   * This call may throw exception. We do not handle it because
   */
  private def startListener(): Unit =
    if optionListener.isDefined then
      continueChecking.set(false) //
      Await.result(optionListener.get, Duration.create(5L, SECONDS))

    optionListener = Some(
      Future {
        val joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
        val topic = new TopicPartition(Domain.generateJoinId(me.userId), 0)
        joinConsumer.assign(java.util.List.of(topic))
        joinConsumer.seek(topic, joinOffset)
        // this may throw IllegalArgumentException if joining topic exists but in db we do not have updated offset
        // and inserted value of joinOffset (taken from db) is -1.

        // not matter if optionListener returned error or ended normally, we simply reassign it
        while (continueChecking.get()) {
          val records: ConsumerRecords[String, String] = joinConsumer.poll(java.time.Duration.ofMillis(1000))
          records.forEach(
            (r: ConsumerRecord[String, String]) => {
              val userID = UUID.fromString(r.key())
              val chatId = r.value()
              chatsToJoin.addOne((userID, chatId))
              joinOffset = r.offset()
              ExternalDB.findChatAndUsers(me, chatId) match {
                case Right((chat: Chat, users: List[User])) =>
                  // if chat exists in our chat list, we do not need to add them again.
                  MyAccount.getMyChats.find(chatAndExecutor => chatAndExecutor._1.chatId == chat.chatId) match {
                    case Some(_) => () // do nothing, we must not add this chat to list of chats
                    case None =>
                      users.find(_.userId == userID) match {
                        case Some(user) => println(s"You got invitation from ${user.login} to chat ${chat.chatName} ")
                        case None       => println(s"Warning!?! Inviting user not found???")
                      }
                      MyAccount.addChat(chat, users)
                  }
                case Left(_) => () // if errors, do nothing, because I am added in users_chats
                // so in next logging chat will show up.
              }
            }
          )
        }
        joinConsumer.close()
      }
    )



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
   *
   * @param users
   * @param chat
   */
  private def sendInvitations(users: List[User], chat: Chat): Unit =
    joinProducer.beginTransaction()
    users.foreach(u => {
      if u.userId != me.userId then
        val joiningTopicName = Domain.generateJoinId(u.userId)
        joinProducer.send(new ProducerRecord[String, String](joiningTopicName, me.userId.toString, chat.chatId))
    })
    joinProducer.commitTransaction()



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
    joinProducer = KessengerAdmin.createJoiningProducer(me.userId)



  /**
   *
   */
  def closeChatManager(): Option[KafkaError] =
    Try {
      joinProducer.close()
      continueChecking.set(false)
      if optionListener.isDefined then
        optionListener.get.onComplete(tryy =>
          println(s"join consumer closed.")
        )
    } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage(ex) match {
          case Left(kafkaError: KafkaError) => Option(kafkaError)
          case Right(value)                 => None // this never will be called
        }
      case Success(_) => None
    }



  def updateOffset(offset: Long): Unit = joinOffset = offset

  def setTopicCreated(boolean: Boolean) : Unit = topicCreated = boolean












