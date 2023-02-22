package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Message}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import util.KessengerAdmin

import scala.util.Try


object SendMessageActor {

  def props(conf: Configuration, ka: KessengerAdmin): Props =
    Props(new SendMessageActor(conf, ka))

}

class SendMessageActor(conf: Configuration, ka: KessengerAdmin) extends Actor {

  println(s"SendMessageActor started.")

  private val messageProducer: KafkaProducer[String, Message] = ka.createMessageProducer

  override def postStop(): Unit = {
    println(s"SendMessageActor switch off")
    Try { messageProducer.close() }
  }


  override def receive: Receive = {
    case m: Message =>
      println(s"SendMessageActor Message to send: $m")
      messageProducer.send(new ProducerRecord[String, Message](m.chatId, m))
  }


}
