package components.actors

import akka.actor._
import components.db.DbExecutor
import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId

import io.github.malyszaryczlowiek.kessengerlibrary.model.{ChatOffsetUpdate, ChatPartitionsOffsets, Configuration, PartitionOffset}
import play.api.db.Database

import scala.collection.concurrent.TrieMap
// import scala.collection.mutable.{Map => mMap}
import scala.concurrent.{ExecutionContext, Future}

object ChatOffsetUpdateActor {

  def props(conf: Configuration, db: Database, dbec: ExecutionContext)(implicit configurator: KafkaConf): Props =
    Props(new ChatOffsetUpdateActor(conf, db, dbec))

}

class ChatOffsetUpdateActor(conf: Configuration, db: Database, dbec: ExecutionContext)(implicit configurator: KafkaConf) extends Actor {


  private val chats: TrieMap[ChatId, List[PartitionOffset]] = TrieMap.empty

  this.chats.addAll(conf.chats.map(c => (c.chatId, c.partitionOffset)))



  println(s"ChatOffsetUpdateActor --> started.")



  override def postStop(): Unit = {
    println(s"ChatOffsetUpdateActor --> switch off")
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
                println(s"ChatOffsetUpdateActor --> sending chat offset update, $u to DB.")
                dbExecutor.updateChatOffsetAndMessageTime(u.userId, u.chatId, u.lastMessageTime, shiftOffsetPerOne(u.partitionOffsets) )
              })
            }(dbec)
          }
        case None =>
      }
      // adding new chat to list
    case nChat: ChatPartitionsOffsets =>
      println(s"ChatOffsetUpdateActor --> adding new chat to chats, $nChat")
      this.chats.addOne(nChat.chatId -> nChat.partitionOffset)

  }


  private def shiftOffsetPerOne(po: List[PartitionOffset]): List[PartitionOffset] = {
    po.map(v => v.copy(offset = v.offset + 1L))
  }


}
