package components.actors

import akka.actor._
import components.actors.readers.{NewMessageReader, Reader}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message}
import kafka.KafkaAdmin

import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.util.UUID


object NewMessageReaderActor {

  def props(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
            ec: ExecutionContext, actorGroupID: UUID): Props =
    Props(new NewMessageReaderActor(out, parentActor, conf, ka, ec, actorGroupID))

}

class NewMessageReaderActor(out: ActorRef, parentActor: ActorRef,
                            conf: Configuration, ka: KafkaAdmin, ec: ExecutionContext, actorGroupID: UUID) extends Actor {


  private val logger: Logger = LoggerFactory.getLogger(classOf[NewMessageReaderActor]).asInstanceOf[Logger]
  logger.trace(s"NewMessageReaderActor. Starting actor. actorGroupID(${actorGroupID.toString})")


  private val reader = new NewMessageReader(out, parentActor, conf, ka, ec, actorGroupID)



  override def postStop(): Unit = {
    reader.stopReading()
    println(s"NewMessageReaderActor --> switch off")
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"NewMessageReaderActor --> adding new chat to read new MESSAGES from, chatId: $newChat")
      reader.addNewChat( newChat )
  }


}
