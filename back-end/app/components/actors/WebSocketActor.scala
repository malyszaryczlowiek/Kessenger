package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.Message.parseMessage
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.FetchMessagesFrom.parseFetchingOlderMessagesRequest
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets.parseChatPartitionOffsets
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting
import util.{BrokerExecutor, KessengerAdmin}
import akka.actor._
import akka.actor.PoisonPill
import components.actors.readers.{InvitationReader, NewMessageReader, OldMessageReader, WritingReader}
import play.api.db.Database

import collection.concurrent.TrieMap
import java.util.UUID
import scala.concurrent.ExecutionContext

object WebSocketActor {
  def props(out: ActorRef, ka: KessengerAdmin, kec: ExecutionContext, db: Database, dbec: ExecutionContext, be: BrokerExecutor): Props =
    Props(new WebSocketActor(out, ka, kec, db, dbec, be))
}

class WebSocketActor( out: ActorRef, ka: KessengerAdmin, kec: ExecutionContext, db: Database, dbec: ExecutionContext, be: BrokerExecutor ) extends Actor {

  sealed trait ActorNameKey
  case object NewMessageReaderKey  extends ActorNameKey
  case object MessageSenderKey     extends ActorNameKey
  case object OldMessageReaderKey  extends ActorNameKey
  case object WritingSenderKey     extends ActorNameKey
  case object InvitationReaderKey  extends ActorNameKey
  case object ChatOffsetUpdaterKey extends ActorNameKey
  case object WritingReaderKey     extends ActorNameKey


  private val actorId = UUID.randomUUID()



  private val childrenActors: TrieMap[ActorNameKey, ActorRef] = TrieMap.empty


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

                              this.childrenActors.get(OldMessageReaderKey) match {
                                case Some(ref) => ref ! c.chatId
                                case None =>
                              }
                          }
                        case Right(newChat: ChatPartitionsOffsets) =>
                          println(s"6. GOT NEW_CHAT_ID: $newChat")
                          this.be.addNewChat(newChat)

                          // we add new chat to listen new messages, old messages and writing
                          this.childrenActors.get(NewMessageReaderKey) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                          this.childrenActors.get(OldMessageReaderKey) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                          this.childrenActors.get(WritingReaderKey) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                          this.childrenActors.get(ChatOffsetUpdaterKey) match {
                            case Some(ref) => ref ! newChat
                            case None =>
                          }
                      }
                    case Right(update: ChatOffsetUpdate) =>
                      println(s"5. GOT CHAT_OFFSET_UPDATE: $update")
                      this.be.updateChatOffset(update)
                      this.childrenActors.get(ChatOffsetUpdaterKey) match {
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
                      (ChatOffsetUpdaterKey, context.actorOf( ChatOffsetUpdateActor.props( conf, db, dbec) )),
                      (InvitationReaderKey,  context.actorOf( InvitationReaderActor.props(new InvitationReader(out, self, conf, this.ka, this.kec) ))),
                      (NewMessageReaderKey,  context.actorOf( NewMessageReaderActor.props(new NewMessageReader(out, self, conf, this.ka, this.kec) ))),
                      (OldMessageReaderKey,  context.actorOf( OldMessageReaderActor.props(new OldMessageReader(out, self, conf, this.ka, this.kec) ))),
                      (MessageSenderKey,     context.actorOf( SendMessageActor.props(conf, ka) )),
                      (WritingSenderKey,     context.actorOf( SendWritingActor.props(conf, ka) )),
                      (WritingReaderKey,     context.actorOf( WritingReaderActor.props(   new WritingReader(out, self, conf, ka, this.kec)         ))),
                    )
                  )
              }
            case Right(message) =>
              println(s"3. GOT MESSAGE: $s")
              this.be.sendMessage(message)
              this.childrenActors.get(MessageSenderKey) match {
                case Some(ref) => ref ! message
                case None =>
              }
          }
        case Right(w) =>
          println(s"2. GOT WRITING: $w")
          this.be.sendWriting( w )
          this.childrenActors.get(WritingSenderKey) match {
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















































