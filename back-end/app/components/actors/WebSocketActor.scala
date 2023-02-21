package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.FetchMessagesFrom.parseFetchingOlderMessagesRequest
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets.parseChatPartitionOffsets
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting
import util.BrokerExecutor
import akka.actor._
import akka.actor.PoisonPill

import collection.concurrent.TrieMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext

object WebSocketActor {
  def props(out: ActorRef, kec: ExecutionContext, dbec: ExecutionContext, be: BrokerExecutor): Props =
    Props(new WebSocketActor(out, kec, dbec, be))
}

class WebSocketActor( out: ActorRef, kec: ExecutionContext, dbec: ExecutionContext, be: BrokerExecutor ) extends Actor {

  sealed trait ActorName
  case object NewMessageReader  extends ActorName
  case object MessageSender     extends ActorName
  case object OldMessageReader  extends ActorName
  case object WritingSender     extends ActorName
  case object InvitationReader  extends ActorName
  case object ChatOffsetUpdater extends ActorName
  case object WritingReader     extends ActorName


  private val actorId = UUID.randomUUID()



  private val childrenActors: TrieMap[ActorName, ActorRef] = TrieMap.empty


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
                        case Right(newChat: ChatPartitionsOffsets) =>
                          println(s"6. GOT NEW_CHAT_ID: $newChat")
                          this.be.addNewChat(newChat)

                          // we add new chat to listen new messages, old messages and writing
                          this.childrenActors.get(NewMessageReader) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                          this.childrenActors.get(OldMessageReader) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                          this.childrenActors.get(WritingReader) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                      }
                    case Right(update: ChatOffsetUpdate) =>
                      println(s"5. GOT CHAT_OFFSET_UPDATE: $update")
                      this.be.updateChatOffset(update)
                      this.childrenActors.get(ChatOffsetUpdater) match {
                        case Some(ref) => ref ! update
                        case None =>
                      }

                  }
                case Right(conf: Configuration) =>
                  println(s"4. GOT CONFIGURATION: $conf")
                  // todo tutaj jak mymy konfigurację to powinniśmy utworzyć aktora do fetchowania starych wiadomości.
                  this.be.initialize(conf)

                  // initialize all child actors
                  this.childrenActors.addAll(
                    List(
                      (ChatOffsetUpdater, context.actorOf( ChatOffsetUpdateActor.props( conf, this.dbec )              )),
                      (InvitationReader,  context.actorOf( InvitationReaderActor.props(out, conf, this.kec)  )),
                      (NewMessageReader,  context.actorOf( NewMessageReaderActor.props(out, conf, this.kec)  )),
                      (OldMessageReader,  context.actorOf( OldMessageReaderActor.props(out, conf, this.kec)  )),
                      (MessageSender,     context.actorOf( SendMessageActor.props( conf )                   )),
                      (WritingSender,     context.actorOf( SendWritingActor.props( conf )                   )),
                      (WritingReader,     context.actorOf( WritingReaderActor.props( out, conf, this.kec )   )),
                    )
                  )
              }
            case Right(message) =>
              println(s"3. GOT MESSAGE: $s")
              this.be.sendMessage(message)
              this.childrenActors.get(MessageSender) match {
                case Some(ref) => ref ! message
                case None =>
              }
          }
        case Right(w) =>
          println(s"2. GOT WRITING: $w")
          this.be.sendWriting( w )
          this.childrenActors.get(WritingSender) match {
            case Some(ref) => ref ! w
            case None =>
          }
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
zmiana systemu aktorów



 */


















































