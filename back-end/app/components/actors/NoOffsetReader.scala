package components.actors

import akka.actor.ActorRef
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}

import scala.concurrent.ExecutionContext

abstract class NoOffsetReader(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends Reader {

  override protected def startReading[A](c: Class[A]): Unit = ??? //tutaj


  override protected def stopReading(): Unit = {

  }


  override protected def addNewChat(newChat: ChatPartitionsOffsets): Unit = {

  }

}
