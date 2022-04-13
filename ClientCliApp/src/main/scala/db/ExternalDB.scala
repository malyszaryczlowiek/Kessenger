package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLType, Statement}
import java.util.{Properties, UUID}
import scala.util.{Try, Using}

class ExternalDB extends DataBase: // [A <: Queryable](statement: A)

  // var connection: Connection = ExternalDB.
  // import com.github.malyszaryczlowiek.db.ExternalDB.connection

  var connection: Connection = ExternalDB.getConnection

  def createUser(login: Login, pass: Password): QueryResult[User] =
    Using ( connection.prepareStatement( "INSERT INTO users (login, pass)  VALUES (?, ?)") ) {      // "INSERT INTO users(login,pass) VALUES (?,?)"
      (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
        //statement.setString(1, UUID.randomUUID())
        statement.setString(1, login)
        statement.setString(2, pass)
        val affectedRows: Int = statement.executeUpdate()//executeQuery()//executeUpdate()
        connection.commit()
        // if affectedRows == 1 then
        // val result = statement.executeUpdate( PostgresStatements.createUser(login, pass) )//executeUpdate()
        if affectedRows > 0 then
          println(s"Affected on $affectedRows") // (user_id, login, pass)
//          val resultSet = statement.getResultSet
//          resultSet.next()
//          val user_id: UserID = resultSet.getObject(1, classOf[UUID])
//          val login_db: Login = resultSet.getString(2)
//          resultSet.close()
          // statement.close()
          Right(User(UUID.randomUUID(), login))
        else
          Left(QueryError("User with this login exists now. Please select another login: "))
    }


  def createChat(chatId: ChatId, chatName: ChatName): QueryResult[Chat] =
    Using ( connection.prepareStatement( "INSERT INTO chats(chat_id,chat_name) VALUES (?,?)" ) ) {
      (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
        statement.setString(1, chatId)
        statement.setString(2, chatName)
        val affectedRows = statement.executeUpdate()
        if affectedRows == 1 then
          val resultSet = statement.getResultSet
          val chat_id: ChatId = resultSet.getString("chat_id")
          val chat_name: Login   = resultSet.getString("chat_name")
          resultSet.close()
          Right(Chat(chat_id, chat_name))
        else
          Left(QueryError("Oooppppsss some error with chat creation"))
    }


  def findUsersChats(user: User): QueryResult[Seq[Chat]] = ???
  def findUsersChats(userId: UserID): QueryResult[Seq[Chat]] = ???
  def findUsersChats(login: Login): QueryResult[Seq[Chat]] = ???
  def findUser(login: Login): QueryResult[User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE login = ?" ) ) { statement => // ,      Statement.RETURN_GENERATED_KEYS
      statement.setString(1, login)
      val resultSet = statement.executeQuery()
      if resultSet.next() then
        val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
        val login: Login   = resultSet.getString("login")
        resultSet.close()
        Right(User(userId, login))
      else
        resultSet.close()
        Left(QueryError("User with this login does not exists."))
    }

  def findUser(userId: UserID): QueryResult[User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE user_id = ?" ) ) { statement => // ,      Statement.RETURN_GENERATED_KEYS
      statement.setObject(1,userId)
      val resultSet = statement.executeQuery()
      if resultSet.next() then
        val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
        val login: Login   = resultSet.getString("login")
        resultSet.close() // TODO do naprawy
        statement.close()
        Right(User(userId, login))
      else
        resultSet.close()
        statement.close()
        Left(QueryError("User with this login does not exists."))
    }



  def updateUsersPassword(user: User, pass: Password): QueryResult[Boolean] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): QueryResult[ChatName] = ???
  def updateUsersChat(userId: UserID, chatId: ChatId): QueryResult[Boolean] = ???  // add user to chat



  def deleteUserPermanently(user: User): QueryResult[User] = ???
  def deleteUserPermanently(userId: UserID): QueryResult[User] = ???
  def deleteUserFromChat(chatId: ChatId, userID: UserID): QueryResult[User] = ???
  def deleteChat(chatId: ChatId): QueryResult[Chat] = ???




//

object ExternalDB:

  private val dbUrl = "jdbc:postgresql://localhost:5438/kessenger_schema"
  private val dbProps = new Properties
  dbProps.setProperty("user","admin")
  dbProps.setProperty("password","passw")
  Class.forName("org.postgresql.Driver")
  private var connection: Connection = DriverManager.getConnection(dbUrl, dbProps)
  connection.setAutoCommit(false)

  def closeConnection(): Try[Unit] = Try { connection.close() }
  protected def getConnection: Connection = connection
  protected def recreateConnection(): Try[Unit] = Try {
    if connection.isClosed then
      connection = DriverManager.getConnection(dbUrl, dbProps)
      connection.setAutoCommit(false)
    else
      closeConnection()
      connection = DriverManager.getConnection(dbUrl, dbProps)
      connection.setAutoCommit(false)
  }

