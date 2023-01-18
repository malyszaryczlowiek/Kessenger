package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, Invitation, Message}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets.parseChatPartitionOffsets
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting

import util.BrokerExecutor
import akka.actor._
import akka.actor.PoisonPill

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object WebSocketActor {
  def props(out: ActorRef, be: BrokerExecutor): Props =
    Props(new WebSocketActor(out, be))
}

class WebSocketActor( out: ActorRef, be: BrokerExecutor ) extends Actor {



  private val actorId = UUID.randomUUID()


  /*
  We can switch off actor only when num of actor listeners decreases to zero
   */
  // private val numOfListeners = new AtomicInteger(0)


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
                      parseChatPartitionOffsets(s) match {
                        case Left(_) =>
                          println(s"6. CANNOT PARSE NewChatId")
                          if (s.equals("PoisonPill")) {
                            println(s"7. GOT PoisonPill '$s'")
                            self ! PoisonPill
                          }
                          else
                            println(s"'$s' is different from PoisonPill.")
                        case Right(chat) =>
                          println(s"6. GOT NEW_CHAT_ID: $chat")
                          this.be.addNewChat(chat)
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
      println(s". Unreadable message. ")
      out ! ("Got Unreadable message")
      self ! PoisonPill

  }

  this.be.setSelfReference( self )

  println(s"0. Actor started")

}







