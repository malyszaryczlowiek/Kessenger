package components.actors

import akka.actor.ActorRef
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration}

import scala.concurrent.ExecutionContext

abstract class OffsetReader(out: ActorRef, conf: Configuration, ec: ExecutionContext) extends Reader {

  // utwórz konsumera, który konsumuje obiekty typu A
  override protected def startReading[A](c: Class[A]): Unit =  {
    c.get
  } //tutaj



  override protected def stopReading(): Unit = {

  }



  override protected def addNewChat(newChat: ChatPartitionsOffsets): Unit = {

  }



  protected def fetchPreviousOffsets(): Unit = {

  }

}
