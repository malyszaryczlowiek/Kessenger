package components.actors

import akka.actor._
import components.actors.readers.Reader
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatPartitionsOffsets, Configuration, Message}

import scala.concurrent.ExecutionContext



object NewMessageReaderActor {

  def props(reader: Reader): Props =
    Props(new NewMessageReaderActor(reader))

}

class NewMessageReaderActor(reader: Reader) extends Actor {

  println(s"NewMessageReaderActor --> started.")

  // initialize i start reading powinny być uruchamiane w konstruktorze  Readera



  override def postStop(): Unit = {
    reader.stopReading()
    println(s"NewMessageReaderActor --> switch off")
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"NewMessageReaderActor --> adding new chat to read new MESSAGES from, chatId: $newChat")
      reader.addNewChat( newChat )
  }


}
