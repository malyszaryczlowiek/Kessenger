package components.actors

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, Configuration}

import scala.concurrent.ExecutionContext

object ChatOffsetUpdateActor {

  def props(conf: Configuration, dbec: ExecutionContext): Props =
    Props(new ChatOffsetUpdateActor(conf, dbec))

}

class ChatOffsetUpdateActor(conf: Configuration, dbec: ExecutionContext) extends Actor {

  println(s"ChatOffsetUpdateActor started.")

  override def postStop(): Unit = {
    println(s"ChatOffsetUpdateActor switch off")
  }

  override def receive: Receive = {
    case u: ChatOffsetUpdate =>
      println(s"ChatOffsetUpdateActor updating chat offset, $u")

  }


}
