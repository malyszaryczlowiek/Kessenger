package com.github.malyszaryczlowiek
package messages

import domain.Domain.{ChatId, Sender}

import com.github.malyszaryczlowiek.domain.User
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

import java.util.UUID


/**
 * Klasa chat wewnętrznie musi używać api Kessenger Admin
 */
class Chat(me: UUID, chatId: ChatId) :

  private val producer: KafkaProducer[UUID, Message] = KessengerAdmin.createProducer(chatId)
  private val consumer: KafkaConsumer[UUID, Message] = KessengerAdmin.createConsumer(chatId)

  def sendMessage(message: Message): Unit =
    val record: java.util.concurrent.Future[RecordMetadata] =
      producer.send(new ProducerRecord[UUID, Message](chatId, me, message))


  def handleIncomingMessage(handler: () => Any): Unit = ???



