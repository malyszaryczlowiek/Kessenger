package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Writing}

import scala.concurrent.ExecutionContext


object WritingReaderActor {
  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new WritingReaderActor(out, conf, ec))
}


class WritingReaderActor(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends NoOffsetReader(out, conf, ec) with Actor {


  println(s"WritingReaderActor started.")
  super.startReading[Writing](classOf[Writing])


  override def postStop(): Unit = {
    println(s"WritingReaderActor switch off")
    this.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"WritingReaderActor adding new chat to read WRITING from, chatId: $newChat")
      this.addNewChat(newChat)
  }


}
