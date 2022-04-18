package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.*//{QueryError, QueryErrorType, QueryErrorMessage}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID, generateChatId}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLType, Statement}
import java.util.{Properties, UUID}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try, Using}

class ExternalDB extends DataBase: // [A <: Queryable](statement: A)

  var connection: Connection = ExternalDB.getConnection

  def createUser(login: Login, pass: Password): Either[QueryErrors,User] =
    Using(connection.prepareStatement("INSERT INTO users (login, pass)  VALUES (?, ?)")) {
      (statement: PreparedStatement) => // , Statement.RETURN_GENERATED_KEYS
        statement.setString(1, login)
        statement.setString(2, pass)
        val affectedRows: Int  = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          findUser(login)
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
    } match {
      case Failure(ex) =>
        if ex.getMessage.contains("duplicate key value violates unique constraint") then
          Left(QueryErrors(List(QueryError( QueryErrorType.ERROR, QueryErrorMessage.LoginTaken))))
        else if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
      case Success(either) => either
    }

  /**
   * Chat creation
   * @param chatId
   * @param chatName
   * @return
   */
  def createChat(users: List[User], chatName: ChatName): Either[QueryErrors,Chat] =
    // TODO search users by calling in separate threads.
    val listBuffer: ListBuffer[QueryErrors] = ListBuffer()
    users.map[Either[QueryErrors,User]](findUser)
    .filter {
        case Left(queryErrors: QueryErrors) => true
        case Right(value) => false
    }
    .foreach {
      case Left(queryErrors) => listBuffer.addOne(queryErrors)
      case Right(value) => ()
    }
    val filtered: List[QueryError] = listBuffer.toList
      .flatMap[QueryError](_.listOfErrors)
      .foldLeft[List[QueryError]](List.empty[QueryError])(
        (list, queryError) =>
          if list.contains(queryError) then list
          else list.appended(queryError)
      )
    if filtered.nonEmpty then
      Left(QueryErrors(filtered))
    else
      val listSize = users.length
      if listSize < 2 then
        Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.AtLeastTwoUsers))))
      else if listSize == 2 then
        val chatId: ChatId = Domain.generateChatId(users.head.userId, users(1).userId)
        insertChatAndChatUsers(users, chatId, chatName)
      else // for more than two users we generate random chat_id
        val chatId: ChatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
        insertChatAndChatUsers(users, chatId, chatName)
  end createChat


  private def insertChatAndChatUsers(user: List[User], chatId: ChatId, chatName: ChatName): Either[QueryErrors, Chat] =
    insertChat(chatId, chatName) match {
      case left @ Left(_) => left
      case Right(chat)    => assignUsersToChat(user, chat)
    }

  private def insertChat(chatId: ChatId, chatName: ChatName): Either[QueryErrors, Chat] =
    Using(connection.prepareStatement("INSERT INTO chats(chat_id, chat_name) VALUES (?,?)")) {
      (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
        statement.setString(1, chatId)
        statement.setString(2, chatName)
        val affectedRows = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          Right( Chat(chatId, chatName) )
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then // no connection to db
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else if ex.getMessage.contains("duplicate key value violates unique constraint") then // very impossible but chat_id duplication.
          Left(QueryErrors(List(QueryError( QueryErrorType.FATAL_ERROR, QueryErrorMessage.DataProcessingError))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
      case Success(either) => either
    }

  private def assignUsersToChat(users: List[User], chat: Chat): Either[QueryErrors, Chat] =
    // todo beter change from batch add to step by step adding
    Using(connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id) VALUES (?, ?)")) {
      (statement: PreparedStatement) =>
        users.foreach(
          user => { // need to add curly braces {} because of intellij syntax error highlighting
            statement.setString(1, chat.chatId)
            statement.setObject(2, user.userId)
            statement.addBatch()
          }
        )
        val numAffected = statement.executeBatch()
        if numAffected.size == users.size then
          Right(Chat(chat.chatId, chat.chatName))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.WARNING, QueryErrorMessage.SomeUsersNotAddedToChat))))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else if ex.getMessage.contains("was aborted: ERROR: insert or update on table \"users_chats\" violates foreign key constraint") then
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.TryingToAddNonExistingUser))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
      case Success(either) => either
    }



  def findUser(user: User): Either[QueryErrors, User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE user_id=? AND login=?" ) ) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        statement.setString(2, user.login)
        Using (statement.executeQuery()) {
          (resultSet: ResultSet) =>
            if resultSet.next() then
              val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
              val login: Login   = resultSet.getString("login")
              Right(User(userId, login))
            else
              Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UserNotFound(user.login)))))
        } match {
          case Failure(ex) => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError(ex.getMessage)))))
      case Success(either) => either
    }



  def findUser(login: Login): Either[QueryErrors,User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE login = ?" ) ) { statement => // ,      Statement.RETURN_GENERATED_KEYS
      statement.setString(1, login)
      Using(statement.executeQuery()) {
        (resultSet: ResultSet) =>
          if resultSet.next() then
            val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
            val login: Login = resultSet.getString("login")
            Right(User(userId, login))
          else
            Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UserNotFound(login)))))
      } match {
        case Failure(ex) => throw ex
        case Success(either) => either
      }
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
      case Success(either) => either
    }



  def findUsersChats(user: User): Either[QueryErrors,Seq[Chat]] =
    Using(connection.prepareStatement("SELECT users_chats.chat_id AS chat_id, chats.chat_name AS chat_name FROM users_chats INNER JOIN chats WHERE users_chats.user_id = chats.chat_id AND users_chats.user_id = ? ")) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        val list: ListBuffer[Chat] = ListBuffer()
        Using(statement.executeQuery()) {
          (resultSet: ResultSet) =>
            while (resultSet.next())
              val chatId: ChatId = resultSet.getString("chat_id")
              val chatName: ChatName = resultSet.getString("chat_name")
              list += Chat(chatId, chatName)
            val seq = list.toSeq
            if seq.isEmpty then Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.DataProcessingError))))
            else Right(seq)
        } match {
          case Failure(ex)     => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command"
          || ex.getMessage == "This connection has been closed." then // no connection to db
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
        else
          Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError())))) // other errors
      case Success(either) => either
    }

  def findUsersChats(userId: UserID): Either[QueryErrors,Seq[Chat]] = ???
  def findUsersChats(login: Login): Either[QueryErrors,Seq[Chat]] = ???



  def updateUsersPassword(user: User, pass: Password): Either[QueryErrors,User] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): Either[QueryErrors,ChatName] = ???

  def addNewUsersToChat(userIds: List[UserID], chatId: ChatId): List[Either[QueryErrors,UserID]] =
    userIds.map( (userId: UserID) =>
      Using( connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id) VALUES (?, ?)")) {
        (statement: PreparedStatement) =>
          statement.setString(1, chatId)
          statement.setObject(2, userId)
          val affectedUpdates: Int = statement.executeUpdate()
          connection.commit()
          if affectedUpdates == 1 then
            Right(userId)
          else
            Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UndefinedError()))))
      } match {
        case Failure(ex) =>
          if ex.getMessage.contains("duplicate key value violates unique constraint") then
            Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.LoginTaken)) ))// TODO change to another error type
          else if ex.getMessage == "FATAL: terminating connection due to administrator command"
            || ex.getMessage == "This connection has been closed." then
            Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
          else
            Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))
        case Success(either) => either
      }
    )




  // add user to  existing chat
  // add user to chat


  def deleteUserPermanently(user: User): Either[QueryErrors,User] = ???
  def deleteUserPermanently(userId: UserID): Either[QueryErrors,User] = ???
  def deleteUsersFromChat(chatId: ChatId, userID: UserID): Either[QueryErrors,User] = ???
  def deleteChat(chatId: ChatId): Either[QueryErrors,Chat] = ???





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

