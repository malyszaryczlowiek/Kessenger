package components.actors


import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.FetchMessagesFrom.parseFetchingOlderMessagesRequest
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets.parseChatPartitionOffsets
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting
import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo.parseSessionInfo

import util.KafkaAdmin
import akka.actor._
import akka.actor.PoisonPill
import components.actors.readers.{InvitationReader, NewMessageReader, OldMessageReader, WritingReader}
import components.util.converters.JsonParsers
import conf.KafkaConf
import play.api.db.Database

import collection.concurrent.TrieMap
import java.util.UUID

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}
import scala.concurrent.ExecutionContext

object WebSocketActor {
  def props(out: ActorRef, ka: KafkaAdmin, kec: ExecutionContext, db: Database, dbec: ExecutionContext, actorId: UUID)(implicit configurator: KafkaConf): Props =
    Props(new WebSocketActor(out, ka, kec, db, dbec, actorId))
}

class WebSocketActor(out: ActorRef, ka: KafkaAdmin, kec: ExecutionContext, db: Database, dbec: ExecutionContext, actorId: UUID )(implicit configurator: KafkaConf) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[WebSocketActor]).asInstanceOf[Logger]
  logger.setLevel(Level.TRACE)
  logger.trace(s"Starting Actor ${actorId.toString} ")

  sealed trait ActorNameKey
  case object NewMessageReaderKey  extends ActorNameKey
  case object MessageSenderKey     extends ActorNameKey
  case object OldMessageReaderKey  extends ActorNameKey
  case object WritingSenderKey     extends ActorNameKey
  case object InvitationReaderKey  extends ActorNameKey
  case object ChatOffsetUpdaterKey extends ActorNameKey
  case object WritingReaderKey     extends ActorNameKey
  case object SessionUpdateKey     extends ActorNameKey


  private val jsonParser:     JsonParsers                     = new JsonParsers
  private val childrenActors: TrieMap[ActorNameKey, ActorRef] = TrieMap.empty


  override def postStop(): Unit = {
    logger.info(s"SWITCH OFF ACTOR ${actorId.toString}")
  }

  def receive: Receive = {
    case s: String =>
      logger.trace(s"WS message in actor ${actorId.toString}")
      parseWriting( s ) match {
        case Left(_) =>
          logger.trace(s"cannot parse writing in actor ${actorId.toString}")
          jsonParser.parseUserAndMessage(s) match {
            case Left(_) =>
              logger.trace(s"cannot parse message in actor ${actorId.toString}")
              parseConfiguration(s) match {
                case Left(_) =>
                  logger.trace(s"cannot parse configuration in actor ${actorId.toString}")
                  parseChatOffsetUpdate(s) match {
                    case Left(_) =>
                      logger.trace(s"cannot parse chat offset update in actor ${actorId.toString}")
                      parseChatPartitionOffsets(s) match {
                        case Left(_) =>
                          logger.trace(s"cannot parse NewChatId in actor ${actorId.toString}")
                          parseSessionInfo(s) match {
                            case Left(_) =>
                              logger.trace(s"cannot parse SessionInfo in actor ${actorId.toString}")
                              parseFetchingOlderMessagesRequest(s) match {
                                case Left(_) =>
                                  logger.trace(s"cannot parse FetchingOlderMessages in actor ${actorId.toString}")
                                  if (s.equals("PoisonPill")) {
                                    logger.trace(s"got PoisonPill in actor ${actorId.toString}")
                                    self ! PoisonPill
                                  }
                                  else
                                    logger.warn(s"cannot parse message ${s} in actor ${actorId.toString}")
                                case Right(c) =>
                                  logger.trace(s"got FetchingOlderMessages in actor ${actorId.toString}")
                                  this.childrenActors.get(OldMessageReaderKey) match {
                                    case Some(ref) => ref ! c.chatId
                                    case None =>
                                  }
                              }
                            case Right(sessionInfo) =>
                              logger.trace(s"got SessionInfo in actor ${actorId.toString}")
                              this.childrenActors.get(SessionUpdateKey) match {
                                case Some(ref) => ref ! sessionInfo
                                case None =>
                              }
                          }
                        case Right(newChat: ChatPartitionsOffsets) =>
                          logger.trace(s"got newChatID $newChat  in actor ${actorId.toString}")
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
                      logger.trace(s"got chatOffsetUpdate in actor ${actorId.toString}")
                      this.childrenActors.get(ChatOffsetUpdaterKey) match {
                        case Some(ref) => ref ! update
                        case None =>
                      }
                  }
                case Right(conf: Configuration) =>
                  logger.trace(s"got Configuration in actor ${actorId.toString}")
                  this.childrenActors.addAll(
                    List(
                      (ChatOffsetUpdaterKey, context.actorOf( ChatOffsetUpdateActor.props(conf, db, dbec) )),
                      (InvitationReaderKey,  context.actorOf( InvitationReaderActor.props(new InvitationReader(out, self, conf, this.ka, this.kec) ))),
                      (NewMessageReaderKey,  context.actorOf( NewMessageReaderActor.props(new NewMessageReader(out, self, conf, this.ka, this.kec) ))),
                      (OldMessageReaderKey,  context.actorOf( OldMessageReaderActor.props(new OldMessageReader(out, self, conf, this.ka, this.kec) ))),
                      (MessageSenderKey,     context.actorOf( SendMessageActor.props(     conf, ka) )),
                      (WritingSenderKey,     context.actorOf( SendWritingActor.props(     conf, ka) )),
                      (WritingReaderKey,     context.actorOf( WritingReaderActor.props(   new WritingReader(out, self, conf, ka, this.kec) ))),
                      (SessionUpdateKey,     context.actorOf( SessionUpdateActor.props(   db, dbec ))),
                    )
                  )
                  out ! "{\"comm\":\"opened correctly\"}"
              }
            case Right((user, message)) =>
              logger.trace(s"got MESSAGE to send in actor ${actorId.toString}")
              this.childrenActors.get(MessageSenderKey) match {
                case Some(ref) => ref ! (user, message)
                case None =>
              }
          }
        case Right(w) =>
          logger.trace(s"got Writing in actor ${actorId.toString}")
          this.childrenActors.get(WritingSenderKey) match {
            case Some(ref) => ref ! w
            case None =>
          }
      }

    case _ =>
      logger.warn(s"Unreadable message")
      out ! ("Got Unreadable message")
      self ! PoisonPill

  }


}















































