package components.actors

import akka.actor._
import components.actors.readers.InvitationReader
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration
import kafka.KafkaAdmin

import java.util.UUID
import scala.concurrent.ExecutionContext


object InvitationReaderActor {
  def props(out: ActorRef, self: ActorRef, conf: Configuration, ka: KafkaAdmin,
            kec: ExecutionContext, actorGroupID: UUID): Props =
    Props(new InvitationReaderActor(out, self, conf, ka, kec, actorGroupID))
}


class InvitationReaderActor(out: ActorRef, self: ActorRef, conf: Configuration,
                            ka: KafkaAdmin, kec: ExecutionContext, actorGroupID: UUID) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[InvitationReaderActor]).asInstanceOf[Logger]
  logger.trace(s"InvitationReaderActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  private val reader = new InvitationReader(out, self, conf, ka, kec, actorGroupID)


  override def postStop(): Unit = {
    logger.trace(s"postStop. Stopping actor. actorGroupID(${actorGroupID.toString})")
    reader.stopReading()
  }

  override def receive: Receive = {
    case _: Any =>
  }

}
