package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Writing}

object SendWritingActor {

  def props(conf: Configuration): Props =
    Props(new SendWritingActor(conf))

}

class SendWritingActor(conf: Configuration) extends Actor {

  println(s"SendWritingActor started.")

  override def postStop(): Unit = {
    println(s"SendWritingActor switch off")
  }


  override def receive: Receive = {
    case w: Writing =>
      println(s"SendWritingActor writing to send: $w")
  }


}
