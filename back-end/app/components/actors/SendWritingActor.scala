package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing
import kafka.KafkaAdmin
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.util.Try
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger

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
    Try {
      this.writingProducer.close()
      logger.trace(s"postStop. writingProducer closed normally. actorGroupID(${actorGroupID.toString})")
    }
  }


  override def receive: Receive = {
    case w: Writing =>
      logger.trace(s"receive. Writing to send: $w. actorGroupID(${actorGroupID.toString})")
      val writingTopic = Domain.generateWritingId(w.chatId)
      writingProducer.send(new ProducerRecord[String, Writing](writingTopic, w))
  }


}
