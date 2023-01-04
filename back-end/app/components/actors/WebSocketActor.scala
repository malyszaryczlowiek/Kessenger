package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaProductionConfigurator
import io.github.malyszaryczlowiek.kessengerlibrary.model.Invitation
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Invitation.parseInvitation
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage

import components.util.converters.JsonParsers


import util.BrokerExecutor
import akka.actor._
import akka.actor.PoisonPill


import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer

object WebSocketActor {
  def props(out: ActorRef, jsonParser: JsonParsers, dbec: ExecutionContext) =
    Props(new WebSocketActor(out, jsonParser, new BrokerExecutor(None, out, new KafkaProductionConfigurator, dbec)))
}

class WebSocketActor( out: ActorRef,
                      jsonParser: JsonParsers,
                      be: BrokerExecutor,
                      messageBuffer: ListBuffer[Message] = ListBuffer.empty[Message],
                      invitationBuffer: ListBuffer[Invitation] = ListBuffer.empty[Invitation]
                    ) extends Actor {





  override def postStop(): Unit = {
    this.be.clearBroker()
    println(s"5. Wyłączyłem actora.")

    // TODO here we cloase all resources used in actor
    // tutaj będzie trzeba zpisywać wszystkie dane o tym która
    // wiadomość (która partycja którego czatu jaki ma offset) została przeczytana
    // i to będę zpisywał w db

    // someResource.close()
  }

  def receive: Receive = {
    case s: String =>

      parseMessage(s) match {
        case Left(_) =>
          println(s"CANNOT PARSE MESSAGE")
          parseInvitation(s) match {
            case Left(_) =>
              println(s"CANNOT PARSE INVITATION")
              parseConfiguration(s) match {
                case Left(_) =>
                  println(s"CANNOT PARSE CONFIGURATION")
                  if (s.equals("PoisonPill")) {
                    println(s"4. Otrzymałem PoisonPill $s")
                    out ! ("4. Wyłączam czat.")
                    self ! PoisonPill
                  }
                case Right(conf) =>
                  println(s"1. initializing Broker")
                  this.be.initialize(conf)
              }
            case Right(invitation) =>
              this.be.sendInvitation( invitation )
          }
        case Right(message) =>
          println(s"2. Processing message. $s")
          this.be.sendMessage( message )
      }

    case _ =>
      println(s"3a. Nieczytelna wiadomość. ")
      out ! ("3b. Dostałem nieczytelną wiadomość.")
      self ! PoisonPill

  }

  println(s"0. Włączam aktora")

}







