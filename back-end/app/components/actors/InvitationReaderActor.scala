package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}

import scala.concurrent.ExecutionContext


object InvitationReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new InvitationReaderActor(out, conf, ec))

}



class InvitationReaderActor(out: ActorRef, conf: Configuration, private val ec: ExecutionContext) extends Actor {

  println(s"InvitationReaderActor started.")

  override def postStop(): Unit = {
    println(s"InvitationReaderActor switch off")
  }

  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"InvitationReaderActor adding new chat to read from, chatId: $newChat")
  }



}
