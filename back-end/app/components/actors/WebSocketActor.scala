package components.actors

import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Configuration.parseConfiguration
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatOffsetUpdate.parseChatOffsetUpdate
import io.github.malyszaryczlowiek.kessengerlibrary.model.FetchMessagesFrom.parseFetchingOlderMessagesRequest
import io.github.malyszaryczlowiek.kessengerlibrary.model.ChatPartitionsOffsets.parseChatPartitionOffsets
import io.github.malyszaryczlowiek.kessengerlibrary.model.Writing.parseWriting
import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo.parseSessionInfo

import util.JsonParsers
import kafka.KafkaAdmin

import akka.actor._
import akka.actor.PoisonPill
import ch.qos.logback.classic.Logger
import collection.concurrent.TrieMap
import conf.KafkaConf
import java.util.UUID
import org.slf4j.LoggerFactory
import play.api.db.Database
import scala.concurrent.ExecutionContext



object WebSocketActor {
  def props(out: ActorRef, ka: KafkaAdmin, kec: ExecutionContext, db: Database,
            dbec: ExecutionContext, actorId: UUID)(implicit configurator: KafkaConf): Props =
    Props(new WebSocketActor(out, ka, kec, db, dbec, actorId))
}


class WebSocketActor(out: ActorRef, ka: KafkaAdmin, kec: ExecutionContext, db: Database,
                     dbec: ExecutionContext, actorGroupID: UUID)(implicit configurator: KafkaConf)  extends Actor {



  private val logger: Logger = LoggerFactory.getLogger(classOf[WebSocketActor]).asInstanceOf[Logger]
  logger.trace(s"WebSocketActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  private sealed trait ActorNameKey
  private case object NewMessageReaderKey  extends ActorNameKey
  private case object MessageSenderKey     extends ActorNameKey
  private case object OldMessageReaderKey  extends ActorNameKey
  private case object WritingSenderKey     extends ActorNameKey
  private case object InvitationReaderKey  extends ActorNameKey
  private case object ChatOffsetUpdaterKey extends ActorNameKey
  private case object WritingReaderKey     extends ActorNameKey
  private case object SessionUpdateKey     extends ActorNameKey


  private val jsonParser:     JsonParsers                     = new JsonParsers
  private val childrenActors: TrieMap[ActorNameKey, ActorRef] = TrieMap.empty


  override def postStop(): Unit = {
    logger.trace(s"WebSocketActor. SWITCH OFF. actorGroupID(${actorGroupID.toString})")
  }

  def receive: Receive = {
    case s: String =>
      // logger.trace(s"receive. New WS message. actorGroupID(${actorGroupID.toString})")
      parseWriting( s ) match {
        case Left(_) =>
          // logger.trace(s"receive. cannot parse writing. actorGroupID(${actorGroupID.toString})")
          jsonParser.parseUserAndMessage(s) match {
            case Left(_) =>
              // logger.trace(s"receive. cannot parse message. actorGroupID(${actorGroupID.toString})")
              parseConfiguration(s) match {
                case Left(_) =>
                  // logger.trace(s"receive. cannot parse configuration. actorGroupID(${actorGroupID.toString})")
                  parseChatOffsetUpdate(s) match {
                    case Left(_) =>
                      // logger.trace(s"receive. cannot parse chat offset update. actorGroupID(${actorGroupID.toString})")
                      parseChatPartitionOffsets(s) match {
                        case Left(_) =>
                          // logger.trace(s"receive. cannot parse NewChatId. actorGroupID(${actorGroupID.toString})")
                          parseSessionInfo(s) match {
                            case Left(_) =>
                              // logger.trace(s"receive. cannot parse SessionInfo. actorGroupID(${actorGroupID.toString})")
                              parseFetchingOlderMessagesRequest(s) match {
                                case Left(_) =>
                                  // logger.trace(s"receive. Cannot parse FetchingOlderMessages. actorGroupID(${actorGroupID.toString})")
                                  if (s.equals("PoisonPill")) {
                                    // logger.trace(s"receive. got PoisonPill. actorGroupID(${actorGroupID.toString})")
                                    self ! PoisonPill
                                  }
                                  else if (s.equals("ping")) {
                                    // logger.trace(s"receive. Ping message. ${actorGroupID.toString})")
                                  }
                                  else
                                    logger.warn(s"receive. cannot parse message $s. actorGroupID(${actorGroupID.toString})")
                                case Right(c) =>
                                  logger.trace(s"receive. got FetchingOlderMessages. actorGroupID(${actorGroupID.toString})")
                                  this.childrenActors.get(OldMessageReaderKey) match {
                                    case Some(ref) => ref ! c.chatId
                                    case None =>
                                  }
                              }
                            case Right(sessionInfo) =>
                              logger.trace(s"receive. got SessionInfo. actorGroupID(${actorGroupID.toString})")
                              this.childrenActors.get(SessionUpdateKey) match {
                                case Some(ref) => ref ! sessionInfo
                                case None =>
                              }
                          }
                        case Right(newChat: ChatPartitionsOffsets) =>
                          logger.trace(s"receive. got newChatID $newChat. actorGroupID(${actorGroupID.toString})")
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
                      logger.trace(s"receive. got chatOffsetUpdate. actorGroupID(${actorGroupID.toString})")
                      this.childrenActors.get(ChatOffsetUpdaterKey) match {
                        case Some(ref) => ref ! update
                        case None =>
                      }
                  }
                case Right(conf: Configuration) =>
                  logger.trace(s"receive. got Configuration. actorGroupID(${actorGroupID.toString})")
                  this.childrenActors.addAll(
                    List(
                      (ChatOffsetUpdaterKey, context.actorOf( ChatOffsetUpdateActor.props(conf, db, dbec, actorGroupID)           )),
                      (InvitationReaderKey,  context.actorOf( InvitationReaderActor.props(out, self, conf, ka, kec, actorGroupID) )),
                      (NewMessageReaderKey,  context.actorOf( NewMessageReaderActor.props(out, self, conf, ka, kec, actorGroupID) )),
                      (OldMessageReaderKey,  context.actorOf( OldMessageReaderActor.props(out, self, conf, ka, kec, actorGroupID) )),
                      (MessageSenderKey,     context.actorOf( SendMessageActor.     props(ka, actorGroupID)                       )),
                      (WritingSenderKey,     context.actorOf( SendWritingActor.     props(ka, actorGroupID)                       )),
                      (WritingReaderKey,     context.actorOf( WritingReaderActor.   props(out, self, conf, ka, kec, actorGroupID) )),
                      (SessionUpdateKey,     context.actorOf( SessionUpdateActor.   props(db, dbec, actorGroupID )                )),
                    )
                  )
                  out ! "{\"comm\":\"opened correctly\"}"
              }
            case Right((user, message)) =>
              logger.trace(s"receive. got MESSAGE to send. actorGroupID(${actorGroupID.toString})")
              this.childrenActors.get(MessageSenderKey) match {
                case Some(ref) => ref ! (user, message)
                case None =>
              }
          }
        case Right(w) =>
          // logger.trace(s"receive. got Writing. actorGroupID(${actorGroupID.toString})")
          this.childrenActors.get(WritingSenderKey) match {
            case Some(ref) => ref ! w
            case None =>
          }
      }

    case _ =>
      logger.warn(s"receive. Unreadable message. actorGroupID(${actorGroupID.toString})")
      out ! "Got Unreadable message"
      self ! PoisonPill

  }


}















































