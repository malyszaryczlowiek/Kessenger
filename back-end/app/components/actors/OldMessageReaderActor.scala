package components.actors

import akka.actor._
import components.actors.readers.{OldMessageReader, Reader}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}
import kafka.KafkaAdmin

import java.util.UUID
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger


object OldMessageReaderActor {

  def props(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
            ec: ExecutionContext, actorGroupID: UUID): Props =
    Props(new OldMessageReaderActor(out, parentActor, conf, ka ,ec, actorGroupID))

}

class OldMessageReaderActor(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
                            ec: ExecutionContext, actorGroupID: UUID) extends  Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[OldMessageReaderActor]).asInstanceOf[Logger]
  logger.trace(s"OldMessageReaderActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  private val reader = new OldMessageReader(out, parentActor, conf, ka ,ec, actorGroupID)


  override def postStop(): Unit = {
    reader.stopReading()
    logger.trace(s"postStop. Stopping actor. actorGroupID(${actorGroupID.toString})")
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      logger.trace(s"receive. Adding new chat. actorGroupID(${actorGroupID.toString})")
      reader.addNewChat(newChat)
    case chatId: String =>
      logger.trace(s"receive. Fetching data from new chatId: $chatId. actorGroupID(${actorGroupID.toString})")
      reader.fetchOlderMessages(chatId)
  }


}
