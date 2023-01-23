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
                              println(s"7 CANNOT PARSE FetchingOlderMessages")
                              if (s.equals("PoisonPill")) {
                                println(s"8. GOT PoisonPill '$s'")
                                self ! PoisonPill
                              }
                              else
                                println(s"8. '$s' is different from PoisonPill.")
                            case Right(c) =>
                              println(s"7. GOT FetchingREQUEST FROM: $c.chatId")
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


/*

Rządanie ma Origin header
Rządanie ma 1 ważnych sesji.
wszedłem w ActorFlow.
0. Actor started
1. ACTOR_ID: 3eada425-1e60-42a9-8e33-13c707b6d448
2. CANNOT PARSE WRITING
3. CANNOT PARSE MESSAGE
4. GOT CONFIGURATION: Configuration(User(d83abea2-d8cf-4446-8a79-f5488b67ba51,Boner69),0,List())
Jestem w future w BrokerExecutor.
utworzyłem Invitation consumer
utworzyłem Message consumer
utworzyłem Writing consumer
przypisałem invitation consumerowi topic i offset
BrokerExecutor: sending Invitation
1. ACTOR_ID: 3eada425-1e60-42a9-8e33-13c707b6d448
2. CANNOT PARSE WRITING
3. CANNOT PARSE MESSAGE
4. CANNOT PARSE CONFIGURATION
5. CANNOT PARSE CHAT_OFFSET_UPDATE
6. GOT NEW_CHAT_ID: ChatPartitionsOffsets(chat--ca19cd85-416a-439e-b13b-784638bb42fa--d83abea2-d8cf-4446-8a79-f5488b67ba51,List(PartitionOffset(0,0), PartitionOffset(1,0), PartitionOffset(2,0)))
DODAłEM chat do dodania do listy czatów
zaczynam dodawanie nowego chatu do listy
wszystkie MESSAGE consumery uruchomione.
!!! NEW CHAT ADDED !!!.
Try Adding new chat succeded. succeeded.

 */



