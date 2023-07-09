package components.actors

import akka.actor._
import components.db.DbExecutor
import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration, PartitionOffset}
import play.api.db.Database

import java.util.UUID
import scala.collection.concurrent.TrieMap
// import scala.collection.mutable.{Map => mMap}
import scala.concurrent.{ExecutionContext, Future}

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}


object ChatOffsetUpdateActor {

  def props(conf: Configuration, db: Database, dbec: ExecutionContext, actorGroupID: UUID)(implicit configurator: KafkaConf): Props =
    Props(new ChatOffsetUpdateActor(conf, db, dbec, actorGroupID))

}

class ChatOffsetUpdateActor(conf: Configuration, db: Database, dbec: ExecutionContext, actorGroupID: UUID)(implicit configurator: KafkaConf) extends Actor {


  private val chats: TrieMap[ChatId, List[PartitionOffset]] = TrieMap.empty
  this.chats.addAll(conf.chats.map(c => (c.chatId, c.partitionOffset)))

  private val logger: Logger = LoggerFactory.getLogger(classOf[ChatOffsetUpdateActor]).asInstanceOf[Logger]
  logger.trace(s"ChatOffsetUpdateActor. Starting actor. actorGroupID(${actorGroupID.toString})")

  override def postStop(): Unit = {
    logger.trace(s"postStop. Stopping actor. actorGroupID(${actorGroupID.toString})")
  }

  override def receive: Receive = {
    // update chat
    case u: ChatOffsetUpdate =>
      this.chats.get(u.chatId) match {
        case Some(t) =>
          val isGrater = u.partitionOffsets.map(_.offset).sum > t.map(_.offset).sum
          if (isGrater) {
            this.chats.put(u.chatId, u.partitionOffsets)
            Future {
              val dbExecutor = new DbExecutor(configurator)
              db.withConnection( implicit connection => {
                logger.trace(s"receive. Updating chat offset. actorGroupID(${actorGroupID.toString})")
                dbExecutor.updateChatOffsetAndMessageTime(u.userId, u.chatId, u.lastMessageTime, shiftOffsetPerOne(u.partitionOffsets) )
              })
            }(dbec)
          }
        case None =>
      }
      // adding new chat to list
    case nChat: ChatPartitionsOffsets =>
      logger.trace(s"receive. Adding new chat to chat list. actorGroupID(${actorGroupID.toString})")
      this.chats.addOne(nChat.chatId -> nChat.partitionOffset)

  }


  private def shiftOffsetPerOne(po: List[PartitionOffset]): List[PartitionOffset] = {
    po.map(v => v.copy(offset = v.offset + 1L))
  }


}
