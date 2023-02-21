package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}

import scala.concurrent.ExecutionContext



object NewMessageReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new NewMessageReaderActor(out, conf, ec))

}

class NewMessageReaderActor(out: ActorRef, conf: Configuration, private val ec: ExecutionContext) extends Actor {

  println(s"NewMessageReaderActor started.")


  override def postStop(): Unit = {
    println(s"NewMessageReaderActor switch off")
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"NewMessageReaderActor adding new chat to read new MESSAGES from, chatId: $newChat")
  }


}
