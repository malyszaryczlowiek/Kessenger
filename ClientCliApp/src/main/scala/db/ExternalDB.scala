package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.*
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID, generateChatId}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLType, Savepoint, Statement}
import java.util.{Properties, UUID}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, duration}
import scala.util.{Failure, Success, Try, Using}

class ExternalDB extends DataBase:

  var connection: Connection = ExternalDB.getConnection

  /**
   * not modify
   * @param login
   * @param pass
   * @return
   */
  def createUser(login: Login, pass: Password): Either[QueryErrors,User] =
    Using(connection.prepareStatement("INSERT INTO users (login, pass)  VALUES (?, ?)")) {
      (statement: PreparedStatement) => // , Statement.RETURN_GENERATED_KEYS
        statement.setString(1, login)
        statement.setString(2, pass)
        val affectedRows: Int  = statement.executeUpdate()
        if affectedRows == 1 then
          findUser(login) // for prove that user is created and for retrieval of usersID
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


  def createChat(users: List[User], chatName: ChatName): Either[QueryErrors,Chat] =
    val listSize = users.length
    if listSize < 2 then
      Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.AtLeastTwoUsers))))
    else
      // TODO search users by calling in separate threads ???
      val listBuffer: ListBuffer[QueryErrors] = ListBuffer()
      users.map[Either[QueryErrors,User]](findUser)
      .filter {
          case Left(queryErrors: QueryErrors) => true
          case Right(value) => false
      }
      .foreach {
        case Left(queryErrors) => listBuffer.addOne(queryErrors)
        case Right(value) => () // do nothing because Right objects were filtered out earlier.
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
        connection.setAutoCommit(false)
        val beforeAnyInsertions: Savepoint = connection.setSavepoint()
        var chatId: ChatId = ""
        if listSize == 2 then chatId = Domain.generateChatId(users.head.userId, users(1).userId)
        else chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID()) // for more than two users we generate random chat_id
        val chat = Chat(chatId, chatName)
        insertChatAndAssignUsersToChat(users, chat) match {
          case Failure(ex) =>
            connection.rollback(beforeAnyInsertions) // we roll back any insertions
            handleExceptionMessage[Chat](ex)
          case Success(either) => either
        }
  end createChat


  private def insertChatAndAssignUsersToChat(users: List[User], chat: Chat): Try[Either[QueryErrors, Chat]] =
    insertChat(chat) match {
      case Failure(exception) => throw exception
      case Success(value) =>
        if value == 1 then assignUsersToChat(users, chat)
        else Try { Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.DataProcessingError)))) }
    }


  private def insertChat(chat: Chat): Try[Int] =
    Using(connection.prepareStatement("INSERT INTO chats(chat_id, chat_name) VALUES (?,?)")) {
      (statement: PreparedStatement) =>
        statement.setString(1, chat.chatId)
        statement.setString(2, chat.chatName)
        statement.executeUpdate()
    }

  /**
   *
   *
   *
   * NOTE:
   * according to jdbc postgresql documentation:
   * https://jdbc.postgresql.org/documentation/81/thread.html
   * connection object is thread safe. So it is possible to call
   * multiple statement in different threads at the same time.
   *
   * @param users
   * @param chat
   * @return
   */
  private def assignUsersToChat(users: List[User], chat: Chat): Try[Either[QueryErrors, Chat]] =
    Try {
      implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
      val affectionList: List[Future[Int]] = users.map(
        user =>
          Future {
            Using(connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id) VALUES (?, ?)")) {
              (statement: PreparedStatement) =>
                statement.setString(1, chat.chatId)
                statement.setObject(2, user.userId)
                statement.executeUpdate()
            } match {
              case Failure(exception) => throw exception
              case Success(value) => value
            }
          }
      )
      val zippedFuture = affectionList.reduceLeft((f1, f2) => f1.zipWith(f1)(_+_)) // we zip all futures when they end.
      val totalAffectedRows: Int = Await.result(zippedFuture, Duration.create(5L, duration.SECONDS))
      if totalAffectedRows == users.length then
        connection.commit()
        Right(chat)
      else
        throw new Exception("Not all Users added to chat.")
    }



  /**
   * not modify
   * @param user
   * @return
   */
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


  /**
   * not modify.
   * @param login
   * @return
   */
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



  def updateUsersPassword(user: User, pass: Password): Either[QueryErrors,User] = ???
  def updateMyLogin(me: User, newLogin: Login, pass: Password): Either[QueryErrors,User] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): Either[QueryErrors,ChatName] = ???


  def addNewUsersToChat(userIds: List[User], chat: Chat): Either[QueryErrors,Chat] = ???



  /**
   * TODO method to delete
   * @param userIds
   * @param chatId
   * @return
   */
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

  def deleteMyAccountPermanently(user: User): Either[QueryErrors,User] = ???
  def deleteUsersFromChat(chat: Chat, users: List[User]): Either[QueryErrors, List[User]] = ??? // if your role in chat is Admin
  def deleteChat(chat: Chat): Either[QueryErrors,Chat] = ??? // if your role in chat is Admin

  private def handleExceptionMessage[A](ex: Throwable): Either[QueryErrors, A] =
    if ex.getMessage == "FATAL: terminating connection due to administrator command"
      || ex.getMessage == "This connection has been closed." then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
    else if ex.getMessage.toLowerCase.contains("timeout") then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.TimeOutError))))
    else if ex.getMessage.contains("duplicate key value violates unique constraint") then
      Left(QueryErrors(List(QueryError( QueryErrorType.FATAL_ERROR, QueryErrorMessage.DataProcessingError))))
    else if ex.getMessage.contains("was aborted: ERROR: insert or update on table \"users_chats\" violates foreign key constraint") then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.TryingToAddNonExistingUser))))
    else
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError()))))


object ExternalDB:

  private val dbUrl = "jdbc:postgresql://localhost:5438/kessenger_schema"
  private val dbProps = new Properties
  dbProps.setProperty("user","admin")
  dbProps.setProperty("password","passw")
  Class.forName("org.postgresql.Driver")
  private var connection: Connection = DriverManager.getConnection(dbUrl, dbProps)

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

