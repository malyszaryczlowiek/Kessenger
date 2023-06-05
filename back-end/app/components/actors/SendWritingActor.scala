package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Writing}
import kafka.KafkaAdmin
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.util.Try
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.util.UUID

object SendWritingActor {

  def props(ka: KafkaAdmin, actorGroupID: UUID): Props =
    Props(new SendWritingActor(ka, actorGroupID))

}

class SendWritingActor(ka: KafkaAdmin, actorGroupID: UUID) extends Actor {

  private val writingProducer: KafkaProducer[String, Writing] = ka.createWritingProducer
  private val logger: Logger = LoggerFactory.getLogger(classOf[SendWritingActor]).asInstanceOf[Logger]
  logger.trace(s"SendWritingActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  override def postStop(): Unit = {
    println(s"SendWritingActor --> switch off")
    Try {
      this.writingProducer.close()
      println(s"SendWritingActor --> postStop() closed normally.")
    }
  }


  override def receive: Receive = {
    case w: Writing =>
      println(s"SendWritingActor -->  writing to send: $w")
      val writingTopic = Domain.generateWritingId(w.chatId)
      writingProducer.send(new ProducerRecord[String, Writing](writingTopic, w))
  }


}
