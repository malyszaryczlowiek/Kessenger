package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ Invitation, Message, ChatOffsetUpdate, UserOffsetUpdate}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Invitation.parseInvitation
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.UserOffsetUpdate.parseUserOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.Chat.parseNewChatId

import util.BrokerExecutor

import akka.actor._
import akka.actor.PoisonPill

import scala.collection.mutable.ListBuffer

object WebSocketActor {
  def props(out: ActorRef, be: BrokerExecutor) =
    Props(new WebSocketActor(out, be))
}

class WebSocketActor( out: ActorRef,
                      be: BrokerExecutor,
                      messageBuffer: ListBuffer[Message] = ListBuffer.empty[Message],
                      invitationBuffer: ListBuffer[Invitation] = ListBuffer.empty[Invitation]
                    ) extends Actor {





  override def postStop(): Unit = {
    this.be.clearBroker()
    println(s"5. Wyłączyłem actora.")
  }

  def receive: Receive = {
    case s: String =>
      parseMessage(s) match {
        case Left(_) =>
          println(s"5a. CANNOT PARSE MESSAGE")
          parseChatOffsetUpdate(s) match {
            case Left(_) =>
              println(s"4a. CANNOT PARSE CHAT_OFFSET_UPDATE")
              parseConfiguration(s) match {
                case Left(_) =>
                  println(s"1a. CANNOT PARSE CONFIGURATION")
                  parseNewChatId(s) match {
                    case Left(_) =>
                      println(s"1a. CANNOT PARSE NewChatId")
                      if (s.equals("PoisonPill")) {
                        println(s"4. Otrzymałem PoisonPill $s")
                        out ! ("4. Wyłączam czat.")
                        self ! PoisonPill
                      }
                      else
                        println(s"'$s' is different from PoisonPill.")
                    case Right(chatId) =>
                      println(s"")
                      this.be.addNewChat(chatId)
                  }
                case Right(conf) =>
                  println(s"1a. initializing Broker")
                  this.be.initialize(conf)
              }
            case Right(update: ChatOffsetUpdate) =>
              println(s"4. Processing ChatOffsetUpdate: $update")
              this.be.updateChatOffset(update)
          }
        case Right(message) =>
          println(s"5. Processing message. $s")
          this.be.sendMessage( message )
      }

    case _ =>
      println(s". Nieczytelna wiadomość. ")
      out ! (". Dostałem nieczytelną wiadomość.")
      self ! PoisonPill

  }

  println(s"0. Włączam aktora")

}







