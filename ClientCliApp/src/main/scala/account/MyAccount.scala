package com.github.malyszaryczlowiek
package account

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.User
import messages.{Chat, ChatExecutor}
import messages.ChatGivens.given

import java.util.UUID
import scala.collection.immutable.SortedMap
import scala.collection.{immutable, mutable}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.parallel.mutable.ParTrieMap
import collection.parallel.CollectionConverters.IterableIsParallelizable

object MyAccount:

  private val myChats: ParTrieMap[Chat, ChatExecutor] = ParTrieMap.empty[Chat, ChatExecutor]
  private var me: User = _

  /**
   *
   * @param user
   */
  def initialize(user: User): Unit =
    ExternalDB.findUsersChats(user) match {
      case Left(QueryErrors(l @ List(QueryError(queryErrorType, description)))) =>
        println(s"Query Error ($description). Cannot initialize user's chats.")
        me = user
      case Right(usersChats: Map[Chat, List[User]]) =>
        println(s"users chats (${usersChats.size}) loaded. ")
        val intermediate = usersChats.map(chatList => (chatList._1, new ChatExecutor(me, chatList._1, chatList._2)))
        myChats.addAll(intermediate)
      case _ =>
        println("Undefined error. Cannot initialize user's chats.")
        me = user
    }

  /**
   * this method comparing to initialize() avoids,
   * sending request to DB.
   * @param user
   */
  def initializeAfterCreation(user: User): Unit = me = user
  def getMyObject: User = me
  def getMyChats: immutable.SortedMap[Chat, ChatExecutor] = myChats.to(immutable.SortedMap)


  /**
   *
   * @return returns Sequence of updated chats,
   *         which must be saved to DB subsequently.
   */
  def shutDownMyAccount(): Seq[Chat] =
    myChats.values.par.map(_.closeChat()).seq.toSeq  // parallel version of closing

  def addChat(chat: Chat, users: List[User]): Unit =
    myChats.addOne((chat, new ChatExecutor(me, chat, users)))


  def removeChat(chat: Chat): Option[ChatExecutor] =
    myChats.remove(chat)