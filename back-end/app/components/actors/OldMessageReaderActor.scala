package components.actors

import akka.actor._
import components.actors.readers.Reader
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets


object OldMessageReaderActor {

  def props(reader: Reader): Props =
    Props(new OldMessageReaderActor(reader: Reader))

}

class OldMessageReaderActor(reader: Reader) extends  Actor {

  println(s"OldMessageReaderActor --> started.")



  override def postStop(): Unit = {
    println(s"OldMessageReaderActor --> switch off")
    reader.stopReading()
  }


  override def receive: Receive = {
    case newChat: ChatPartitionsOffsets =>
      println(s"OldMessageReaderActor --> adding new chat to read new MESSAGES from, chatId: $newChat")
      reader.addNewChat(newChat)
    case chatId: String =>
      println(s"OldMessageReaderActor --> fetching data from new chatId: $chatId")
      reader.fetchOlderMessages(chatId)
  }


}
