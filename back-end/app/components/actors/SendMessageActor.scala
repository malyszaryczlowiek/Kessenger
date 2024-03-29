package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Message, User}
import kafka.KafkaAdmin
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.util.Try
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.util.UUID


object SendMessageActor {

  def props( ka: KafkaAdmin, actorGroupID: UUID): Props =
    Props(new SendMessageActor(ka, actorGroupID))

}

class SendMessageActor(ka: KafkaAdmin, actorGroupID: UUID) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[SendMessageActor]).asInstanceOf[Logger]
  logger.trace(s"SendMessageActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  private val messageProducer: KafkaProducer[User, Message] = ka.createMessageProducer

  override def postStop(): Unit = {
    Try {
      messageProducer.close()
      logger.trace(s"postStop. messageProducer closed normally. actorGroupID(${actorGroupID.toString})")
    }
  }


  override def receive: Receive = {
    case m: (User, Message) =>
      logger.trace(s"receive. Message to send: $m. actorGroupID(${actorGroupID.toString})")
      messageProducer.send(new ProducerRecord[User, Message](m._2.chatId, m._1, m._2))
  }


}
