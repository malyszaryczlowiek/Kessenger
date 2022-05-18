package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.account.MyAccount
import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.*
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorType, KafkaErrorsHandler}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global


/**
 * TODO rewrite this to class it is easier to test
 */
class ChatManager(var me: User, var topicCreated: Boolean = false):

  private var joinProducer: KafkaProducer[String, String] = KessengerAdmin.createJoiningProducer(me.userId)
  private var joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
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
   */
  private def startListener(): Unit =
    /*
        We set our joining consumer to read from our
        joining topic starting from our last read offset.
        Not that even if we find in topic invitation to chat,
        which is currently written in in DB, This invitation
        will not show up twice (See future value below).
        Note
         This call may throw exception. We do not handle it because
       */
    joinConsumer.seek(new TopicPartition(Domain.generateJoinId(me.userId), 0), joinOffset)


    /*
      Start Future in which we listen invitations to chats.
    */
    optionListener = Some(
      Future {
        while (continueChecking.get()) {
          val records: ConsumerRecords[String, String] = joinConsumer.poll(Duration.ofMillis(1000))
          records.forEach(
            (r: ConsumerRecord[String, String]) => {
              val userID = UUID.fromString(r.key())
              val chatId = r.value()
              chatsToJoin.addOne((userID, chatId))
              joinOffset = r.offset()
              ExternalDB.findChatAndUsers(me, chatId) match {
                case Right((chat: Chat, users: List[User])) =>
                  // jeśli czat już jest na mojej liście czatów to nie należy go dodawać
                  MyAccount.getMyChats.find(chatAndExecutor => chatAndExecutor._1.chatId == chat.chatId) match {
                    case Some(_) => () // do nothing, we must not add this chat to list of chats
                    case None =>
                      users.find(_.userId == userID) match {
                        case Some(user) => println(s"You got invitation from ${user.login} to chat ${chat.chatName} ")
                        case None => println(s"Warning!?! Inviting user not found???")
                      }
                      MyAccount.addChat(chat, users)
                  }
                case Left(_) => () // if errors, do nothing, because I am added in users_chats
                // so in next logging chat will show up.
              }
            }
          )
        }
      }
    )


  /**
   * 
   * @return
   */
  private def tryStartAndHandleError(): Option[KafkaError] =
    Try { startListener() } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handle(ex) match {
          case Left(kafkaError) => Some (kafkaError)
          case Right(_)         => None // this will never be called 
        }
      case Success(_) => None
    }


  /**
   * 
   * @return
   */
  def startListening(): Option[KafkaError] = 
    if topicCreated then tryStartAndHandleError()
    else
      /*
        Here we try to create topic
      */
      KessengerAdmin.createJoiningTopic(me.userId) match {
        case Left(kafkaError) => Some(kafkaError) 
        case Right(_) => // joining topic created without errors
          me = me.copy(joiningOffset = 0L) // we need to change joining offset because in db is set to -1
          MyAccount.updateUser(me)
          tryStartAndHandleError()
      }


  /**
   * TODO write integration tests
   *
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
      case Failure(ex) => KafkaErrorsHandler.handle[Chat](ex)
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
    optionJoinProducer match {
      case Some( producer ) =>
        producer.abortTransaction()
        producer.close()
      case None => ()
    }
    recreateProducer()


  private def recreateProducer(): Unit =
    optionJoinProducer = Some( KessengerAdmin.createJoiningProducer(me.userId) )







  def closeChatManager(): Unit =
    optionJoinProducer match {
      case Some( producer ) => producer.close()
      case None => ()
    }
    continueChecking.set(false)
    if future != null then

      future.onComplete(tryy => {
        tryy match {
          case Failure(ex) => ()
          case Success(value) => ???
        }

        optionJoinConsumer.get.close()
        println(s"join consumer closed.")
      })














