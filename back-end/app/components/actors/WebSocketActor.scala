package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.FetchMessagesFrom.parseFetchingOlderMessagesRequest
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
    println(s"9. SWITCH OFF ACTOR.")
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
              parseConfiguration(s) match {
                case Left(_) =>
                  println(s"4. CANNOT PARSE CONFIGURATION")
                  parseChatOffsetUpdate(s) match {
                    case Left(_) =>
                      println(s"5. CANNOT PARSE CHAT_OFFSET_UPDATE")
                      parseChatPartitionOffsets(s) match {
                        case Left(_) =>
                          println(s"6. CANNOT PARSE NewChatId")
                          parseFetchingOlderMessagesRequest(s) match {
                            case Left(_) =>
                              println(s"7. CANNOT PARSE FetchingOlderMessages")
                              if (s.equals("PoisonPill")) {
                                println(s"8. GOT PoisonPill '$s'")
                                self ! PoisonPill
                              }
                              else
                                println(s"8. '$s' is different from PoisonPill.")
                            case Right(c) =>
                              println(s"7. GOT FETCHING OLDER MESSAGES REQUEST FROM: $c.chatId")

                              // todo to trzeba wysłać do innego aktora wraz z referencją
                              //  do aktora out tak aby odpowiedź mogła zostać odesłana z powrotem

                              this.be.fetchOlderMessages(c.chatId)
                          }
                        case Right(chat) =>
                          println(s"6. GOT NEW_CHAT_ID: $chat")
                          this.be.addNewChat(chat)
                      }
                    case Right(update: ChatOffsetUpdate) =>
                      println(s"5. GOT CHAT_OFFSET_UPDATE: $update")
                      this.be.updateChatOffset(update)
                  }
                case Right(conf) =>
                  println(s"4. GOT CONFIGURATION: $conf")
                  // todo tutaj jak mymy konfigurację to powinniśmy utworzyć aktora do fetchowania starych wiadomości.
                  this.be.initialize(conf)
              }
            case Right(message) =>
              println(s"3. GOT MESSAGE: $s")
              this.be.sendMessage(message)
          }
        case Right(w) =>
          println(s"2. GOT WRITING: $w")
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



