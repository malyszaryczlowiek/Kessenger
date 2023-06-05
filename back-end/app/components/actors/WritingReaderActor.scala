package components.actors

import akka.actor._
import components.actors.readers.{Reader, WritingReader}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}
import kafka.KafkaAdmin

import java.util.UUID
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}



object WritingReaderActor {
  def props(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
            ec: ExecutionContext, actorGroupID: UUID): Props =
    Props(new WritingReaderActor(out, parentActor, conf, ka, ec, actorGroupID))
}


class WritingReaderActor(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
                         ec: ExecutionContext, actorGroupID: UUID) extends Actor {


  private val logger: Logger = LoggerFactory.getLogger(classOf[WritingReaderActor]).asInstanceOf[Logger]
  logger.trace(s"WritingReaderActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  private val reader = new WritingReader(out, parentActor, conf, ka, ec, actorGroupID)


  override def postStop(): Unit = {
    println(s"WritingReaderActor --> switch off")
    reader.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"WritingReaderActor --> adding new chat to read WRITING from, chatId: $newChat")
      reader.addNewChat(newChat)
  }


}
