package components.actors

import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets


trait Reader {

  protected def startReading[A](c: Class[A]): Unit

  protected def stopReading(): Unit

  protected def addNewChat(newChat: ChatPartitionsOffsets): Unit
}
