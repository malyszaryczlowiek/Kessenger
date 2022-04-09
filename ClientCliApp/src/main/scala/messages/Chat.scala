package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, JoinId, WritingId}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}

class Chat

object Chat :

  // producers
  private val chatProducer: KafkaProducer[String, String]    = KessengerAdmin.createChatProducer
  private val joinProducer: KafkaProducer[String, String]    = KessengerAdmin.createJoinProducer
  private val writingProducer: KafkaProducer[String, String] = KessengerAdmin.createWritingProducer

  // consumers:
  private val chatConsumer: KafkaConsumer[String, String]    = ???
  private val joinConsumer: KafkaConsumer[String, String]    = ???
  private val writingConsumer: KafkaConsumer[String, String] = ???

  //
  @tailrec
  def askToJoinChat(me: UUID, sendTo: UUID, messege: String): Unit =
    val meUUID = me.toString
    val sendToUUID = sendTo.toString
    val joinId: JoinId = Domain.generateJoinId(sendTo)
    val serverResponse = joinProducer.send(new ProducerRecord[String, String](joinId, meUUID, messege))
    val sendToServer = Try[RecordMetadata] {  serverResponse.get(5000, TimeUnit.MILLISECONDS) }
    sendToServer match {
      case Failure(exception) =>
        if askIfTryAgain(exception.getMessage) == "y" then askToJoinChat(me, sendTo, messege)
        else
          println(s"You aborted connecting with user.")
      case Success(value) =>
        val chatId = Domain.generateChatId(meUUID, sendToUUID)
        val writingId = Domain.generateWritingId(meUUID, sendToUUID)
        KessengerAdmin.createNewChat(chatId, writingId) match
          case Failure(exception) => println(s"EXCEPTION: $exception")
          case Success(value) =>
            println(s"CREATING NEW CHAT: value = $value")
            val chatId: ChatId = Domain.generateChatId(meUUID, sendToUUID)
            val serverResponse = chatProducer.send(new ProducerRecord[String, String](chatId, meUUID, messege)) // send and forget

    }

  private def askIfTryAgain(ex: String): String =
    println(s"ERROR: ${ex}")
    println("Would you like to try again? [y - yes, any key - no]")
    print("> ")
    readLine


  def sendMessage(me: UUID, chatId: ChatId, messege: String): Unit =
    chatProducer.send(new ProducerRecord[String, String](chatId, me.toString, messege))
    chatConsumer.po
