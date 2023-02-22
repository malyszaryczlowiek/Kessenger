package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message}

import scala.concurrent.ExecutionContext



object NewMessageReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new NewMessageReaderActor(out, conf, ec))

}

class NewMessageReaderActor(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends OffsetReader(out, conf, ec) with Actor {

  println(s"NewMessageReaderActor started.")


  // umieść go w startReading(consumer)
  this.startReading[Message](classOf[Message])
  // tam przyassignuj consumera do odpowiednich chatów

  // zaimplementuj dodawanie nowego chatu z reassigningiem listy chatów


  override def postStop(): Unit = {
    println(s"NewMessageReaderActor switch off")
    this.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"NewMessageReaderActor adding new chat to read new MESSAGES from, chatId: $newChat")
      this.addNewChat( newChat )
  }


}
