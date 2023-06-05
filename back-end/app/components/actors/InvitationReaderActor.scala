package components.actors

import akka.actor._
import components.actors.readers.Reader
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.util.UUID


object InvitationReaderActor {
  def props(reader: Reader, actorGroupID: UUID): Props =
    Props(new InvitationReaderActor(reader, actorGroupID))
}


class InvitationReaderActor(reader: Reader, actorGroupID: UUID) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[InvitationReaderActor]).asInstanceOf[Logger]
  logger.trace(s"InvitationReaderActor. Starting actor. actorGroupID(${actorGroupID.toString})")


  override def postStop(): Unit = {
    logger.trace(s"InvitationReaderActor. Stopping actor. actorGroupID(${actorGroupID.toString})")
    reader.stopReading()
  }

  override def receive: Receive = {
    case _: Any =>
      println(s"InvitationReaderActor --> We should do nothing. ")
  }

}
