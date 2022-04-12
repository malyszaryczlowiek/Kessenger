package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.ExternalDB.connection
import com.github.malyszaryczlowiek.db.queries.{QueryError, Queryable}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, Statement}
import java.util.{Properties, UUID}
import scala.util.Try

class ExternalDB extends DataBase: // [A <: Queryable](statement: A)

  def createUser(login: Login, pass: Password): QueryResult[User] = ???
  def createChat(chatId: ChatId, chatName: ChatName): QueryResult[Chat] = ???

  def findUsersChats(user: User): QueryResult[Seq[Chat]] = ???
  def findUsersChats(userId: UserID): QueryResult[Seq[Chat]] = ???
  def findUsersChats(login: Login): QueryResult[Seq[Chat]] = ???
  def findUser(login: Login): QueryResult[User] = ???
  def findUser(userId: UserID): QueryResult[User] = ???

  def updateUsersPassword(user: User, pass: Password): QueryResult[Boolean] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): QueryResult[ChatName] = ???
  def updateUsersChat(userId: UserID, chatId: ChatId): QueryResult[Boolean] = ???  // add user to chat

  def deleteUserPermanently(user: User): QueryResult[User] = ???
  def deleteUserPermanently(userId: UserID): QueryResult[User] = ???
  def deleteUserFromChat(chatId: ChatId, userID: UserID): QueryResult[User] = ???
  def deleteChat(chatId: ChatId): QueryResult[Chat] = ???

  def closeConnection(): Try[Unit] = Try { connection.commit() }


object ExternalDB:

  private val dbUrl = "jdbc:postgresql://localhost:5432/kessenger_schema"
  private val dbProps = new Properties
  dbProps.setProperty("user","admin")
  dbProps.setProperty("password","passw")
  private var connection: Connection = _

  def connectToDb(): Try[Unit] = Try {
    connection = DriverManager.getConnection(dbUrl, dbProps)
  }



