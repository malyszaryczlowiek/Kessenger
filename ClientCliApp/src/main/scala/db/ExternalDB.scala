package com.github.malyszaryczlowiek
package db

import domain.User

import com.github.malyszaryczlowiek.db.ExternalDB.connection
import com.github.malyszaryczlowiek.db.queries.{QueryError, Queryable}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, Statement}
import java.util.{Properties, UUID}
import scala.util.Try

class ExternalDB[A <: Queryable](statement: A) extends DataBase:

  def createUser(user: User): Try[Either[QueryError, User]] = ???
  def createChat(chatId: ChatId, chatName: ChatName): Try[Either[QueryError, Chat]] = ???

  // from Readable
  def readUsersChats(user: User, pass: String): Try[Either[QueryError, Seq[Chat]]] = ???
  def findUser(user: User): Try[Either[QueryError, User]] = ???
  def findUser(login: String): Try[Either[QueryError, User]] = ???

  // from Updatable
  def updateUsersPassword(user: User, pass: String): Try[Either[QueryError, Boolean]] = ???
  def updateChatName(chatId: ChatId, newName: String): Try[Either[QueryError, String]] = ???
  def updateUsersChat(userId: UUID, chatId: ChatId): Try[Either[QueryError, Boolean]] = ???

  // from Deletable
  def deleteUser(user: User): Try[Either[QueryError, User]] = ???
  def deleteUser(userId: UUID): Try[Either[QueryError, User]] = ???
  def deleteChat(chatId: ChatId): Try[Either[QueryError, User]] = ???









  def createUser(newUser: User): Try[User] =
    Try {
      val stat = connection.prepareStatement( statement.createUser() )
      stat.set
    }


  def searchUser(name: String): Try[Option[User]] =
    Try {
      None
    }


  def searchUsersChats(user: User): Try[Option[List[Chat]]] =
    Try {
      None
    }

  def closeConnection(): Try[Unit] =
    Try {
      connection.commit()
    }


object ExternalDB:

  private val dbUrl = "jdbc:postgresql://localhost:5432/kessenger_schema"
  private val dbProps = new Properties
  dbProps.setProperty("user","admin")
  dbProps.setProperty("password","passw")
  private var connection: Connection = _

  def connectToDb(): Try[Unit] =
    Try {
      connection = DriverManager.getConnection(dbUrl, dbProps)
    }



