package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain
import com.github.malyszaryczlowiek.domain.Domain.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}

class ChatManager

object ChatManager:

  // producers
  private val chatProducer: KafkaProducer[String, String]    = KessengerAdmin.createChatProducer()
  private val joinProducer: KafkaProducer[String, String]    = KessengerAdmin.createJoiningProducer()
  // private val writingProducer: KafkaProducer[String, String] = KessengerAdmin.createWritingProducer()

  // consumers:
//  private val chatConsumer: KafkaConsumer[String, String]    = ???
//  private val joinConsumer: KafkaConsumer[String, String]    = ???
//  private val writingConsumer: KafkaConsumer[String, String] = ???

  //
  @tailrec
  def askToJoinChat(me: UUID, sendTo: UUID, messege: String): Unit =
    val meUUID = me.toString
    val joinId: JoinId = Domain.generateJoinId(sendTo)
    val serverResponse = joinProducer.send(new ProducerRecord[String, String](joinId, meUUID, messege))
    val sendToServer = Try[RecordMetadata] {  serverResponse.get(5000, TimeUnit.MILLISECONDS) }
    sendToServer match {
      case Failure(exception) =>
        println(s"Opppps Exception during send to join topic occured: ${exception.getMessage}")
        if askIfTryAgain(exception.getMessage) == "y" then askToJoinChat(me, sendTo, messege)
        else
          println(s"You aborted connecting with user.")
      case Success(value) =>
        val chatId: ChatId = Domain.generateChatId(me, sendTo)
        val writingId: WritingId = Domain.generateWritingId(me, sendTo)
        KessengerAdmin.createNewChat(chatId, writingId) match
          case Failure(exception) =>
            println(s"EXCEPTION during creation of new chat: ${exception.getMessage}")
            try {
              throw new org.apache.kafka.common.errors.TopicExistsException(exception.getMessage)
            }
            catch
              case e: org.apache.kafka.common.errors.TopicExistsException =>
                sendMessage(me, chatId, messege)
          case Success(value) =>
            println(s"CREATING NEW CHAT: value = $value")
            val chatId: ChatId = Domain.generateChatId(me, sendTo)
            val serverResponse = chatProducer.send(
              new ProducerRecord[String, String](chatId, meUUID, messege),
              callBack
            ) // send and forget
    }

  private def askIfTryAgain(ex: String): String =
    println(s"ERROR: ${ex}")
    println("Would you like to try again? [y - yes, any key - no]")
    print("> ")
    readLine


  def sendMessage(me: UUID, chatId: ChatId, messege: String): Unit =
    chatProducer.send(new ProducerRecord[String, String](chatId, me.toString, messege), callBack)

  def closeAll(): Unit =
    chatProducer.close()
    joinProducer.close()
    // writingProducer.close()

  private val callBack: Callback = (metadata: RecordMetadata, exception: Exception) => {
      Option(exception) match {
        case Some(ex) => println(s"Exception during message sent to chat: ${ex.getMessage}")
        case None =>
          print(s"Message sent correctly: Topic: ${metadata.topic()}, Timestamp: ${metadata.timestamp()}, OffSet: ${metadata.offset()}, partition: ${metadata.partition()}\n> ")
      }
    }