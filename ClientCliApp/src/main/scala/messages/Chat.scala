package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, Sender}
import com.github.malyszaryczlowiek.domain.User
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

import java.util.UUID


/**
 * Klasa chat wewnętrznie musi używać api Kessenger Admin
 */
class Chat(me: UUID, chatId: ChatId) :

  private val stringMessageProducer: KafkaProducer[String, String] = KessengerAdmin.createStringMessageProducer()
  // private val stringMessageConsumer: KafkaConsumer[String, String] = KessengerAdmin.createStringMessageConsumer

  def sendStringMessage(message: String): Unit =
    val record: java.util.concurrent.Future[RecordMetadata] =
      stringMessageProducer.send(new ProducerRecord[String, String](chatId, me.toString, message))


  def handleIncomingMessage(handler: () => Any): Unit = ???

  def closeApp(): Unit =
    // I need try to handle possible Exceptions
    stringMessageProducer.close()
    // stringMessageConsumer.close()



