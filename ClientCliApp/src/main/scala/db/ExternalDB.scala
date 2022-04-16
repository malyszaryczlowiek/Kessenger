package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLType, Statement}
import java.util.{Properties, UUID}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try, Using}

class ExternalDB extends DataBase: // [A <: Queryable](statement: A)

  var connection: Connection = ExternalDB.getConnection

  def createUser(login: Login, pass: Password): QueryResult[User] =
    Using(connection.prepareStatement("INSERT INTO users (login, pass)  VALUES (?, ?)")) {
      (statement: PreparedStatement) => // , Statement.RETURN_GENERATED_KEYS
        statement.setString(1, login)
        statement.setString(2, pass)
        val affectedRows: Int  = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          findUser(login)
        else
          Left( QueryError(s"Oooopss... Some Error.. Affected rows: $affectedRows != 1") )
    } match {
      case Failure(ex) =>
        if ex.getMessage.contains("duplicate key value violates unique constraint") then
          Left( QueryError( s"Sorry Login is taken, try with another one."))
        else if ex.getMessage == "FATAL: terminating connection due to administrator command" then
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) )
      case Success(either) => either
    }


  def createChat(chatId: ChatId, chatName: ChatName): QueryResult[Chat] =
    Using ( connection.prepareStatement( "INSERT INTO chats(chat_id, chat_name) VALUES (?,?)" ) ) {
      (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
        statement.setString(1, chatId)
        statement.setString(2, chatName)
        val affectedRows = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          var resultChatId: ChatId = ""
          var resultChatName = ""
          val confirmationQuery = "SELECT chat_id, chat_name FROM chats WHERE chat_id = ? AND chat_name = ?"
          Using(connection.prepareStatement( confirmationQuery )) {
            (innerStatement: PreparedStatement) =>
              innerStatement.setString(1, chatId)
              innerStatement.setString(2, chatName)
              val resultSet: ResultSet = innerStatement.executeQuery()
              if resultSet.next() then
                resultChatId = resultSet.getString("chat_id")
                resultChatName = resultSet.getString("chat_name")
                resultSet.close()
                Right(Chat(chatId, chatName))
              else
                resultSet.close()
                Left(QueryError("Created chat not found in DB"))
          } match {
            case Failure(ex) =>
              Left( QueryError(ex.getMessage) )
            case Success( either ) => either
          }
        else
          Left(QueryError("Oooppppsss some error with chat creation"))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else if ex.getMessage.contains("duplicate key value violates unique constraint") then // very impossible but chat_id duplication.
          Left( QueryError( s"Sorry Login is taken, try with another one."))
        else
          Left( QueryError(ex.getMessage) )
      case Success(either) => either
    }



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
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) ) // other errors
      case Success(either) => either
    }


  def findUser(userId: UserID): QueryResult[User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE user_id = ?" ) ) {
      (statement: PreparedStatement) => //, Statement.RETURN_GENERATED_KEYS
        statement.setObject(1,userId)
        val resultSet = statement.executeQuery()
        if resultSet.next() then
          val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
          val login: Login   = resultSet.getString("login")
          resultSet.close() // TODO do naprawy ????
          statement.close()
          Right(User(userId, login))
        else
          resultSet.close()
          statement.close()
          Left(QueryError("User with this login does not exists."))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) ) // other errors
      case Success(either) => either
    }

  def findUsersChats(user: User): QueryResult[Seq[Chat]] =
    Using(connection.prepareStatement("SELECT users_chats.chat_id AS chat_id, chats.chat_name AS chat_name FROM users_chats INNER JOIN chats WHERE users_chats.user_id = chats.chat_id AND users_chats.user_id = ? ")) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        val resultSet: ResultSet = statement.executeQuery()
        val list: ListBuffer[Chat] = ListBuffer()
        while (resultSet.next())
          val chatId: ChatId = resultSet.getString("chat_id")
          val chatName: ChatName = resultSet.getString("chat_name")
          list += Chat(chatId, chatName)
        resultSet.close()
        val seq = list.toSeq
        if seq.isEmpty then Left(QueryError("Oooppppsss some error with chat creation"))
        else Right(seq)
    } match {
      case Failure(ex) => Left(QueryError(s"Server Error: ${ex.getMessage}"))
      case Success(either) => either
    }

  def findUsersChats(userId: UserID): QueryResult[Seq[Chat]] = ???
  def findUsersChats(login: Login): QueryResult[Seq[Chat]] = ???



  def updateUsersPassword(user: User, pass: Password): QueryResult[Boolean] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): QueryResult[ChatName] = ???
  def addUserToChat(userId: UserID, chatId: ChatId): QueryResult[Boolean] = ???  // add user to  existing chat
  // add user to chat


  def deleteUserPermanently(user: User): QueryResult[User] = ???
  def deleteUserPermanently(userId: UserID): QueryResult[User] = ???
  def deleteUserFromChat(chatId: ChatId, userID: UserID): QueryResult[User] = ???
  def deleteChat(chatId: ChatId): QueryResult[Chat] = ???





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

  def recreateConnection(): Try[Unit] = Try {
    if connection.isClosed then
      connection = DriverManager.getConnection(dbUrl, dbProps)
      connection.setAutoCommit(false)
    else
      closeConnection()
      connection = DriverManager.getConnection(dbUrl, dbProps)
      connection.setAutoCommit(false)
  }

