package components.actors


import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Configuration, Writing}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import util.KafkaAdmin

import scala.util.Try

object SendWritingActor {

  def props(conf: Configuration, ka: KafkaAdmin): Props =
    Props(new SendWritingActor(conf, ka))

}

class SendWritingActor(conf: Configuration, ka: KafkaAdmin) extends Actor {

  println(s"SendWritingActor --> started.")
  private val writingProducer: KafkaProducer[String, Writing] = ka.createWritingProducer

  override def postStop(): Unit = {
    println(s"SendWritingActor --> switch off")
    Try {
      this.writingProducer.close()
      println(s"SendWritingActor --> postStop() closed normally.")
    }
  }


  override def receive: Receive = {
    case w: Writing =>
      println(s"SendWritingActor -->  writing to send: $w")
      val writingTopic = Domain.generateWritingId(w.chatId)
      writingProducer.send(new ProducerRecord[String, Writing](writingTopic, w))
  }


}
