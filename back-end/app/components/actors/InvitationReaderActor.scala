package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Invitation}

import scala.concurrent.ExecutionContext


object InvitationReaderActor {

  def props(out: ActorRef, conf: Configuration, ec: ExecutionContext): Props =
    Props(new InvitationReaderActor(out, conf, ec))

}



class InvitationReaderActor(out: ActorRef, conf: Configuration, private val ec: ExecutionContext) extends OffsetReader(out, conf, ec) with Actor {

  println(s"InvitationReaderActor started.")

  this.startReading[Invitation](classOf[Invitation])

  override def postStop(): Unit = {
    println(s"InvitationReaderActor switch off")
    this.stopReading()
  }

  override def receive: Receive = {
    case _: Any =>
      println(s"InvitationReaderActor We should do nothing. ")
  }




}
