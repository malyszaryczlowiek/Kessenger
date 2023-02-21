package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}

import scala.concurrent.ExecutionContext


object WritingReaderActor {
  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new WritingReaderActor(out, conf, ec))
}


class WritingReaderActor(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends Actor {


  println(s"WritingReaderActor started.")


  override def postStop(): Unit = {
    println(s"WritingReaderActor switch off")
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"WritingReaderActor adding new chat to read WRITING from, chatId: $newChat")
  }


}
