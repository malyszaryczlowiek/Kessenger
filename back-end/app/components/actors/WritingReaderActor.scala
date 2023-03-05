package components.actors

import akka.actor._
import components.actors.readers.Reader
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets



object WritingReaderActor {
  def props(reader: Reader): Props =
    Props(new WritingReaderActor(reader))
}


class WritingReaderActor(reader: Reader) extends Actor {


  println(s"WritingReaderActor started.")


  override def postStop(): Unit = {
    println(s"WritingReaderActor --> switch off")
    reader.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"WritingReaderActor --> adding new chat to read WRITING from, chatId: $newChat")
      reader.addNewChat(newChat)
  }


}
