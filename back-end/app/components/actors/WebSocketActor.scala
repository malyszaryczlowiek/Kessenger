package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, Invitation, Message, UserOffsetUpdate, Writing}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.Chat.parseNewChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting
import util.BrokerExecutor
import akka.actor._
import akka.actor.PoisonPill

import java.util.UUID
import scala.collection.mutable.ListBuffer

object WebSocketActor {
  def props(out: ActorRef, be: BrokerExecutor): Props =
    Props(new WebSocketActor(out, be))
}

class WebSocketActor( out: ActorRef,
                      be: BrokerExecutor,
                      messageBuffer: ListBuffer[Message] = ListBuffer.empty[Message],
                      invitationBuffer: ListBuffer[Invitation] = ListBuffer.empty[Invitation]
                    ) extends Actor {



  private val actorId = UUID.randomUUID()


  override def postStop(): Unit = {
    this.be.clearBroker()
    println(s"8. SWITCH OFF ACTOR.")
  }

  def receive: Receive = {
    case s: String =>
      println(s"1. ACTOR_ID: $actorId")
      parseWriting( s ) match {
        case Left(_) =>
          println(s"2. CANNOT PARSE WRITING")
          parseMessage(s) match {
            case Left(_) =>
              println(s"3. CANNOT PARSE MESSAGE")
              parseChatOffsetUpdate(s) match {
                case Left(_) =>
                  println(s"4. CANNOT PARSE CHAT_OFFSET_UPDATE")
                  parseConfiguration(s) match {
                    case Left(_) =>
                      println(s"5. CANNOT PARSE CONFIGURATION")
                      parseNewChatId(s) match {
                        case Left(_) =>
                          println(s"6. CANNOT PARSE NewChatId")
                          if (s.equals("PoisonPill")) {
                            println(s"7. GOT PoisonPill '$s'")
                            // out ! ("4. Wyłączam czat.")
                            self ! PoisonPill
                          }
                          else
                            println(s"'$s' is different from PoisonPill.")
                        case Right(chatId) =>
                          println(s"6. GOT NEW_CHAT_ID: $chatId")
                          this.be.addNewChat(chatId)
                      }
                    case Right(conf) =>
                      println(s"5. GOT CONFIGURATION: $conf")
                      this.be.initialize(conf)
                  }
                case Right(update: ChatOffsetUpdate) =>
                  println(s"3. GOT CHAT_OFFSET_UPDATE: $update")
                  this.be.updateChatOffset(update)
              }
            case Right(message) =>
              println(s"2. GOT MESSAGE: $s")
              this.be.sendMessage(message)
          }
        case Right(w) =>
          println(s"1. GOT WRITING: $w")
          this.be.sendWriting( w )
      }

    case _ =>
      println(s". Nieczytelna wiadomość. ")
      out ! (". Dostałem nieczytelną wiadomość.")
      self ! PoisonPill

  }

  println(s"0. Włączam aktora")

}







