package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.account.MyAccount
import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.*

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global




/**
 * Jak MyAccount initialize wystartuje to należy tam uruchomić join producera i join consumera.
 * Tylko w taki sposób, że oba muszą włączać się tylko gdy wejdziemy w specjalne miejsce
 * aby sprawdzić czy są jakieś prośby o czaty, jeśli tak to
 */
object ChatManager: 

  private val joinProducer: KafkaProducer[String, String] = KessengerAdmin.createJoiningProducer()
  private val joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()

  private val chatsToJoin: ListBuffer[(UserID, ChatId)] = ListBuffer.empty[(UserID, ChatId)]

  // in this example we dont care about
  // atomicity of this variable. if we want to stop
  // consumer loop simply change this value to false
  var continueChecking = true
  val me: User = MyAccount.getMyObject



  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  val myJoiningTopicPartition = new TopicPartition(Domain.generateJoinId(me.userId), 0)
  var joinOffset: Long = me.joiningOffset

  // we start reading from our joining topic
  joinConsumer.seek(myJoiningTopicPartition, joinOffset)



  /**
   * we ask to join chat
   * @param user user to who we send invitation
   * @param chat chat may be two users chat, or group chat
   * @return
   */
  def askToJoinChat(user: User, chat: Chat): Any = ///???
    val me: User = MyAccount.getMyObject
    val joiningTopicName = Domain.generateJoinId(user.userId)
    joinProducer.send(new ProducerRecord[String, String](joiningTopicName, me.userId.toString, chat.chatId))



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
  private val future = Future {
    while (continueChecking) {
      val records: ConsumerRecords[String, String] = joinConsumer.poll(Duration.ofMillis(1000))
      records.forEach(
        (r: ConsumerRecord[String, String]) => {
          val userID = UUID.fromString(r.key())
          val chatId = r.value()
          chatsToJoin.addOne((userID,chatId))
          joinOffset = r.offset()
          val me = MyAccount.getMyObject
          ExternalDB.findChatAndUsers(me, chatId) match {
            case Right((chat: Chat, users: List[User])) =>
              // jeśli czat już jest na mojej liście czatów to nie należy go dodawać
              MyAccount.getMyChats.find(chatAndExecutor => chatAndExecutor._1.chatId == chat.chatId ) match {
                case Some(_) => () // do nothing, we must not add this chat to list of chats
                case None    =>
                  users.find(_.userId == userID) match {
                    case Some(user) => println(s"You got invitation from ${user.login} to chat ${chat.chatName} ")
                    case None       => println(s"Warning!?! Inviting not found???")
                  }
                  MyAccount.addChat(chat, users)
              }
            case Left(_) => () // if errors do nothing, because I am added in users_chats
            // so in next log in chat will show up.
          }
        }
      )
    }
  }


  def closeChatManager(): Unit =
    joinProducer.close()
    continueChecking = false
    future.onComplete(t => {
      joinConsumer.close()
      println(s"join consumer closed.")
    })













