package components.actors

import akka.actor._
import components.actors.readers.{OldMessageReader, Reader}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}
import kafka.KafkaAdmin

import java.util.UUID
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}


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
    println(s"OldMessageReaderActor --> switch off")
    reader.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"OldMessageReaderActor --> adding new chat to read new MESSAGES from, chatId: $newChat")
      reader.addNewChat(newChat)
    case chatId: String =>
      println(s"OldMessageReaderActor --> fetching data from new chatId: $chatId")
      reader.fetchOlderMessages(chatId)
  }


}
