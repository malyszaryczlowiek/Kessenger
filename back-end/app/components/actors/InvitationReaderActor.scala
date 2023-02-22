package components.actors

import akka.actor._
import components.actors.readers.Reader
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Invitation}

import scala.concurrent.ExecutionContext


object InvitationReaderActor {

  def props(reader: Reader): Props =
    Props(new InvitationReaderActor(reader))

}



class InvitationReaderActor(reader: Reader) extends Actor {

  println(s"InvitationReaderActor started.")


  override def postStop(): Unit = {
    println(s"InvitationReaderActor switch off")
    reader.stopReading()
  }

  override def receive: Receive = {
    case _: Any =>
      println(s"InvitationReaderActor We should do nothing. ")
  }




}
