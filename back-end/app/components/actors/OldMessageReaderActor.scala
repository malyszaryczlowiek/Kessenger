package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration

import scala.concurrent.ExecutionContext

object OldMessageReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new OldMessageReaderActor(out, conf, ec))

}

class OldMessageReaderActor(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends Actor {

  println(s"OldMessageReaderActor started.")


  override def postStop(): Unit = {
    println(s"OldMessageReaderActor switch off")
  }


  // Todo tutaj można w receive utworzyć consumera, wczytać dane, i odesłać wiadomości. z
  override def receive: Receive = {
    case chatId: String =>
      println(s"OldMessageReaderActor fetching data from new chatId: $chatId")
  }


}
