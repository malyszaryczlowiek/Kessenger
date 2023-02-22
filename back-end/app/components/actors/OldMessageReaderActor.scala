package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Message}

import scala.concurrent.ExecutionContext

object OldMessageReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new OldMessageReaderActor(out, conf, ec))

}

class OldMessageReaderActor(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends OffsetReader(out, conf, ec) with Actor {

  println(s"OldMessageReaderActor started.")
  this.startReading[Message](classOf[Message])


  override def postStop(): Unit = {
    println(s"OldMessageReaderActor switch off")
    this.stopReading()
  }


  // Todo tutaj można w receive utworzyć consumera, wczytać dane, i odesłać wiadomości. z
  override def receive: Receive = {
    case chatId: String =>
      println(s"OldMessageReaderActor fetching data from new chatId: $chatId")
  }


}
