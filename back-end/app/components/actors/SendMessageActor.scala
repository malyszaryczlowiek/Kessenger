package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Message}


object SendMessageActor {

  def props(conf: Configuration): Props =
    Props(new SendMessageActor(conf))

}

class SendMessageActor( conf: Configuration) extends Actor {

  println(s"SendMessageActor started.")

  override def postStop(): Unit = {
    println(s"SendMessageActor switch off")
  }


  override def receive: Receive = {
    case m: Message =>
      println(s"SendMessageActor Message to send: $m")

  }


}
