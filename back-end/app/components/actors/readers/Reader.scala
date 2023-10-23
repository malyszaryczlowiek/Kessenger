package components.actors.readers

import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets
import org.apache.kafka.clients.consumer.KafkaConsumer

trait Reader {

  protected def initializeConsumer[K,V](consumer: KafkaConsumer[K, V]): Unit

  def startReading(): Unit

  def stopReading(): Unit

  def addNewChat(newChat: ChatPartitionsOffsets): Unit

  def fetchOlderMessages(chatId: ChatId): Unit = {}

}
