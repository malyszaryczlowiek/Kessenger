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
import scala.concurrent.Future
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer


class ChatManager

/**
 * Jak MyAccount initialize wystartuje to należy tam uruchomić join producera i join consumera.
 * Tylko w taki sposób, że oba muszą włączać się tylko gdy wejdziemy w specjalne miejsce
 * aby sprawdzić czy są jakieś prośby o czaty, jeśli tak to
 */
object ChatManager:

  val chatsToJoin: ListBuffer[(UserID, ChatId)] = ListBuffer()

  private val joinProducer: KafkaProducer[String, String] = KessengerAdmin.createJoiningProducer()
  private val joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()

  
  // we will read from topic with name of chatId. Each chat topic
  // has only one partition (and three replicas)
  val myJoiningTopicPartition = new TopicPartition(Domain.generateJoinId(MyAccount.getMyObject.userId), 0)
  var joinOffset: Long = 0L // = MyAccount.getMyObject.joinOffset TODO implement in future

  // we start reading from our joining topic
  joinConsumer.seek(myJoiningTopicPartition, joinOffset)


  // in this example we dont care about
  // atomicity of this variable. if we want to stop
  // consumer loop simply change this value to false
  var continueChecking = true

  /**
   * This future handle if someone ask me to join a chat.
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
        }
      )
    }
  }

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


  def closeChatManager(): Unit =
    joinProducer.close()
    continueChecking = false
    future.onComplete(t => {
      joinConsumer.close()
      println(s"join consumer closed.")
    })















//  private def askIfTryAgain(ex: String): String =
//    println(s"ERROR: ${ex}")
//    println("Would you like to try again? [y - yes, any key - no]")
//    print("> ")
//    readLine
//
//  private val callBack: Callback = (metadata: RecordMetadata, exception: Exception) => {
//    Option(exception) match {
//      case Some(ex) => println(s"Exception during message sent to chat: ${ex.getMessage}")
//      case None =>
//        print(s"Message sent correctly: Topic: ${metadata.topic()}, Timestamp: ${metadata.timestamp()}, OffSet: ${metadata.offset()}, partition: ${metadata.partition()}\n> ")
//    }
//  }




// consumers:

//  @tailrec
//  def askToJoinChat(me: UUID, sendTo: UUID, messege: String): Unit =
//    val meUUID = me.toString
//    val joinId: JoinId = Domain.generateJoinId(sendTo)
//    val serverResponse = joinProducer.send(new ProducerRecord[String, String](joinId, meUUID, messege))
//    val sendToServer = Try[RecordMetadata] {  serverResponse.get(5000, TimeUnit.MILLISECONDS) }
//    sendToServer match {
//      case Failure(exception) =>
//        println(s"Opppps Exception during send to join topic occured: ${exception.getMessage}")
//        if askIfTryAgain(exception.getMessage) == "y" then askToJoinChat(me, sendTo, messege)
//        else
//          println(s"You aborted connecting with user.")
//      case Success(value) =>
//        val chatId: ChatId = Domain.generateChatId(me, sendTo)
//        val writingId: WritingId = Domain.generateWritingId(me, sendTo)
//        KessengerAdmin.createNewChat(chatId, writingId) match
//          case Failure(exception) =>
//            println(s"EXCEPTION during creation of new chat: ${exception.getMessage}")
//            try {
//              throw new org.apache.kafka.common.errors.TopicExistsException(exception.getMessage)
//            }
//            catch
//              case e: org.apache.kafka.common.errors.TopicExistsException =>
//                sendMessage(me, chatId, messege)
//          case Success(value) =>
//            println(s"CREATING NEW CHAT: value = $value")
//            val chatId: ChatId = Domain.generateChatId(me, sendTo)
//            val serverResponse = chatProducer.send(
//              new ProducerRecord[String, String](chatId, meUUID, messege),
//              callBack
//            ) // send and forget
//    }
