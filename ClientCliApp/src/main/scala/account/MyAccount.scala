package com.github.malyszaryczlowiek
package account

import messages.Chat

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.User

import java.util.UUID

object MyAccount:

  private var me: User = _// User(UUID.randomUUID(), "")

  private var myChats: Map[Chat, List[User]] = _


  /**
   * TODO implement this method to download all data from DB
   * @param user
   */
  def initialize(user: User): Unit =
    ExternalDB.findUsersChats(user) match {
      case Left(QueryErrors(l @ List(QueryError(queryErrorType, description)))) =>
        println("Undefined error. Cannot initialize user's chats.")
        me = user
        myChats = Map.empty[Chat, List[User]]
      case Right(usersChats: Map[Chat, List[User]]) =>
        println(s"users chats (${usersChats.size}) loaded. ")
        myChats = usersChats
      case _ =>
        println("Undefined error. Cannot initialize user's chats.")
        me = user
        myChats = Map.empty[Chat, List[User]]
    }

  def initializeAfterCreation(user: User): Unit =
    me = user
    myChats = Map.empty[Chat, List[User]]

  def getMyObject: User = me