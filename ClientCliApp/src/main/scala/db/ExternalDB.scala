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
import concurrent.ExecutionContext.Implicits.global


object ExternalDB:

  private val dbUrl = "jdbc:postgresql://localhost:5438/kessenger_schema"
  private val dbProps = new Properties
  dbProps.setProperty("user","admin")
  dbProps.setProperty("password","passw")
  Class.forName("org.postgresql.Driver")
  private var connection: Connection = DriverManager.getConnection(dbUrl, dbProps)



  def closeConnection(): Try[Unit] = Try { connection.close() }

  protected def getConnection: Connection = connection

  /**
   * If we lost connection, we need to try to recreate it.
   * @return
   */
  def recreateConnection(): Try[Unit] = Try {
    if connection.isClosed then
      connection = DriverManager.getConnection(dbUrl, dbProps)
      //connection.setAutoCommit(false)
    else
      closeConnection()
      connection = DriverManager.getConnection(dbUrl, dbProps)
      //connection.setAutoCommit(false)
  }


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

  /**
   * NOTE:
   * This method has parallel calls to DB.
   *
   * @param users
   * @param chatName
   * @return
   */
  def createChat(users: List[User], chatName: ChatName): Either[QueryErrors,Chat] =
    val listSize = users.length
    if listSize < 2 then
      Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.AtLeastTwoUsers))))
    else
      val filtered = filterUsersExistingInDb(users)
      if filtered.nonEmpty then
        Left(QueryErrors(filtered))
      else
        connection.setAutoCommit(false)
        val beforeAnyInsertions: Savepoint = connection.setSavepoint()
        var chatId: ChatId = ""
        var groupChat = false
        if listSize == 2 then chatId = Domain.generateChatId(users.head.userId, users(1).userId)
        else
          groupChat = true
          chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID()) // for more than two users we generate random chat_id
        val chat = Chat(chatId, chatName, groupChat, 0L)
        insertChatAndAssignUsersToChat(users, chat) match {
          case Failure(ex) =>
            connection.rollback(beforeAnyInsertions) // we roll back any insertions
            handleExceptionMessage[Chat](ex)
          case Success(either) => either
        }
  end createChat


  /**
   * Method concurrentlyu checks if all users exists in db.
   * If so returned list od QueryErrors is empty.
   * @param users
   * @return
   */
  private def filterUsersExistingInDb(users: List[User]): List[QueryError] = //???
    val listBuffer: ListBuffer[QueryErrors] = ListBuffer()
    val zippedFuture = users.map( user => Future { findUser(user) } )
      .foldLeft[Future[List[Either[QueryErrors,User]]]](Future {List.empty[Either[QueryErrors,User]]})((flist, f) => flist.zipWith(f)((list, either) => list.appended(either)))
    val listOfEither = Await.result(zippedFuture, Duration.create(10L, duration.SECONDS))
    listOfEither.filter {
      case Left(queryErrors: QueryErrors) => true
      case Right(value) => false
    }.foreach {
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
    filtered


  private def insertChatAndAssignUsersToChat(users: List[User], chat: Chat): Try[Either[QueryErrors, Chat]] =
    insertChat(chat) match {
      case Failure(exception) => throw exception
      case Success(value) =>
        if value == 1 then assignUsersToChat(users, chat)
        else Try { Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.DataProcessingError)))) }
    }


  private def insertChat(chat: Chat): Try[Int] =
    Using(connection.prepareStatement("INSERT INTO chats(chat_id, chat_name, group_chat) VALUES (?,?,?)")) {
      (statement: PreparedStatement) =>
        statement.setString(1, chat.chatId)
        statement.setString(2, chat.chatName)
        statement.setBoolean(3, chat.groupChat)
        statement.executeUpdate()
    }

  /**
   *
   *
   *
   * NOTE:
   * according to jdbc postgresql documentation:
   * https://jdbc.postgresql.org/documentation/81/thread.html
   * connection object is thread safe. So it is possible to execute
   * multiple statement in different threads at the same time.
   *
   * @param users
   * @param chat
   * @return
   */
  private def assignUsersToChat(users: List[User], chat: Chat): Try[Either[QueryErrors, Chat]] =
    Try {
      val affectionList: List[Future[Int]] = users.map(
        user =>
          Future {
            //Using(connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id, offset) VALUES (?, ?, ?)")) {
            Using(connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id) VALUES (?, ?)")) {
              (statement: PreparedStatement) =>
                statement.setString(1, chat.chatId)
                statement.setObject(2, user.userId)
                //statement.setLong(3, chat.offset)
                statement.executeUpdate()
            } match {
              case Failure(exception) => throw exception
              case Success(value) => value
            }
          }
      )
      val zippedFuture = affectionList.reduceLeft((f1, f2) => f1.zipWith(f2)(_+_)) // we zip all futures when they end.
      val totalAffectedRows: Int = Await.result(zippedFuture, Duration.create(5L, duration.SECONDS))
      if totalAffectedRows == users.length then
        connection.commit()
        Right(chat)
      else
        throw new Exception("Not all Users added to chat.")
    }


  /**
   * TODO write tests
   * @param login
   * @param password
   * @return
   */
  def findUser(login: Login, password: Password): Either[QueryErrors, User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE login=? AND pass=?" ) ) {
      (statement: PreparedStatement) =>
        statement.setString(1, login)
        statement.setString(2, password)
        Using (statement.executeQuery()) {
          (resultSet: ResultSet) =>
            if resultSet.next() then
              val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
              val login: Login   = resultSet.getString("login")
              Right(User(userId, login))
            else
              Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UserNotFound(login)))))
        } match {
          case Failure(ex) => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex) => handleExceptionMessage[User](ex)
      case Success(either) => either
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
      case Failure(ex) => handleExceptionMessage[User](ex)
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
      case Failure(ex) => handleExceptionMessage[User](ex)
      case Success(either) => either
    }


  /**
   * no modify
   * @param user
   * @return
   */
  def findUsersChats(user: User): Either[QueryErrors,Seq[Chat]] =
    val sql = "SELECT chats.chat_id, chats.chat_name, chats.group_chat, users_chats.users_offset FROM chats " +
      "INNER JOIN users_chats " +
      "ON chats.chat_id = users_chats.chat_id " +
      "WHERE users_chats.user_id = ?"
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        val list: ListBuffer[Chat] = ListBuffer()
        Using(statement.executeQuery()) {
          (resultSet: ResultSet) =>
            while (resultSet.next())
              val chatId: ChatId = resultSet.getString("chat_id")
              val chatName: ChatName = resultSet.getString("chat_name")
              val groupChat: Boolean = resultSet.getBoolean("group_chat")
              val offset: Long = resultSet.getLong("users_offset")
              list += Chat(chatId, chatName, groupChat, offset)
            Right(list.toSeq)
        } match {
          case Failure(ex)     => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex) => handleExceptionMessage[Seq[Chat]](ex)
      case Success(either) => either
    }

  /**
   * Todo write tests
   * @param user
   * @return
   */
  def findUsersChatsMap(user: User): Either[QueryErrors, Map[Chat, List[User]]] =
    val sql = "SELECT my_chats.chat_id, my_chats.chat_name, my_chats.group_chat, my_chats.users_offset, users.user_id, users.login FROM users " +
      "INNER JOIN chats " +
      "ON chats.chat_id = my_chats.chat_id " +
      "INNER JOIN users_chats AS other_chats " +
      "ON users.user_id = other_chats.user_id " +
      "INNER JOIN users_chats AS my_chats " +
      "ON others_chats.chat_id = my_chats.chat_id " +
      "WHERE my_chats.user_id = ?"
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        val buffer: ListBuffer[(Chat, User)] = ListBuffer()
        Using(statement.executeQuery()) {
          (resultSet: ResultSet) =>
            while (resultSet.next())
              val chatId:   ChatId   = resultSet.getString("chat_id")
              val chatName: ChatName = resultSet.getString("chat_name")
              val groupChat: Boolean = resultSet.getBoolean("group_chat")
              val offset:    Long    = resultSet.getLong("users_offset")
              val userId:    UUID    = resultSet.getObject[UUID]("user_id", classOf[UUID])
              val login:     Login   = resultSet.getString("login")
              val chat:      Chat    = Chat(chatId, chatName, groupChat, offset)
              val u:         User    = User(userId, login)
              buffer += ((chat, u))
            val grouped = buffer.toList.groupMap[Chat, User]((chat, user) => chat)((chat, user) => user)
            Right(grouped)
        } match {
          case Failure(ex)     => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex) => handleExceptionMessage[Map[Chat, List[User]]](ex)
      case Success(either) => either
    }


  /**
   * No modify
   * @param user
   * @param oldPass
   * @param newPass
   * @return
   */
  def updateUsersPassword(user: User, oldPass: Password, newPass: Password): Either[QueryErrors,User] =
    Using(connection.prepareStatement("UPDATE users SET pass = ? WHERE user_id = ? AND login = ? AND pass = ?")) {
      (statement: PreparedStatement) =>
        statement.setString(1, newPass)
        statement.setObject(2, user.userId)
        statement.setString(3, user.login)
        statement.setString(4, oldPass)
        statement.executeUpdate()
    } match {
      case Failure(ex) => handleExceptionMessage(ex)
      case Success(value) =>
        if value == 1 then Right(user)
        else Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.IncorrectLoginOrPassword))))
    }


  /**
   * No modify
   * @param me
   * @param newLogin
   * @param pass
   * @return
   */
  def updateMyLogin(me: User, newLogin: Login, pass: Password): Either[QueryErrors,User] =
    findUser(newLogin) match {
      case Right(value) => Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.LoginTaken))))
      case l @ Left(queryErrors: QueryErrors) =>
        if queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.UserNotFound(s"$newLogin") then
          Using(connection.prepareStatement("UPDATE users SET login = ? WHERE user_id = ? AND login = ? AND pass = ?")) {
            (statement: PreparedStatement) =>
              statement.setString(1, newLogin)
              statement.setObject(2, me.userId)
              statement.setString(3, me.login)
              statement.setString(4, pass)
              statement.executeUpdate()
          } match {
            case Failure(ex) => handleExceptionMessage(ex)
            case Success(value) =>
              if value == 1 then Right(User(me.userId, newLogin))
              else Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.IncorrectPassword))))
          }
        else l
    }


  /**
   * No modify
   * @param chat
   * @param newName
   * @return
   */
  def updateChatName(chat: Chat, newName: ChatName): Either[QueryErrors,ChatName] =
    Using(connection.prepareStatement("UPDATE chats SET chat_name = ? WHERE chat_id = ? AND chat_name = ? AND group_chat = ?")) {
      (statement: PreparedStatement) =>
        statement.setString(1, newName)
        statement.setString(2, chat.chatId)
        statement.setString(3, chat.chatName)
        statement.setBoolean(4, chat.groupChat)
        statement.executeUpdate()
    } match {
      case Failure(ex) =>
        handleExceptionMessage(ex)
      case Success(value) =>
        if value == 1 then Right(newName)
        else Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.ChatDoesNotExist(chat.chatName)))))
    }


  /**
   * TODO write tests
   * @param user
   * @param chat
   * @return
   */
  def updateChatOffset(user: User, chat: Chat): Either[QueryErrors,Chat] =
    Using(connection.prepareStatement("UPDATE users_chats SET users_offset = ? WHERE chat_id = ? AND user_id = ? ")) {
      (statement: PreparedStatement) =>
        statement.setLong(1, chat.offset)
        statement.setString(2, chat.chatId)
        statement.setObject(3, user.userId)
        statement.executeUpdate()
    } match {
      case Failure(ex) => handleExceptionMessage(ex)
      case Success(value) =>
        if value == 1 then Right(chat)
        else Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.IncorrectLoginOrPassword))))
    }

  /**
   * no modify
   * @param userIds
   * @param chat
   * @return
   */
  def addNewUsersToChat(users: List[User], chat: Chat): Either[QueryErrors,Chat] =
    if users.isEmpty then
      Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.NoUserSelected))))
    else
      var stateBeforeInsertion: Savepoint = null
      Try {
        connection.setAutoCommit(false)
        stateBeforeInsertion = connection.setSavepoint()
        val filtered = filterUsersExistingInDb(users)
        if filtered.nonEmpty then
          Left(QueryErrors(filtered))
        else // if all users are present in db we can try add them to chat
          connection.setAutoCommit(false)
          val futureList = users.map[Future[Int]](
            user =>
              Future { // each insertion executed in separate thread
                Using(connection.prepareStatement("INSERT INTO users_chats (chat_id, user_id) VALUES (?, ?)")) {
                  (statement: PreparedStatement) =>
                    statement.setString(1, chat.chatId)
                    statement.setObject(2, user.userId)
                    statement.executeUpdate()
                } match {
                  case Failure(ex) => throw ex
                  case Success(value) => value
                }
              }
          )
          val zippedFuture = futureList.reduceLeft((f1, f2) => f1.zipWith(f2)(_+_))
          val affected = Await.result(zippedFuture, Duration.create(5L, duration.SECONDS))
          if affected == users.length then
            connection.commit()
            Right(chat)
          else
            throw new Exception("Data processing error.")
      } match {
        case Failure(ex) =>
          if stateBeforeInsertion != null then connection.rollback(stateBeforeInsertion) // This connection has been closed
          handleExceptionMessage[Chat](ex)  // returns DataProcessing Error
        case Success(either) => either
      }



  /**
   * No modify
   * @param user
   * @return
   */
  def deleteMyAccountPermanently(me: User, pass: Password): Either[QueryErrors,User] =
    Using(connection.prepareStatement("DELETE FROM users WHERE user_id = ? AND login = ? AND pass = ?")) {
      (statement: PreparedStatement) =>
        statement.setObject(1, me.userId)
        statement.setString(2, me.login)
        statement.setString(3, pass)
        statement.executeUpdate()
    } match {
      case Failure(ex) => handleExceptionMessage(ex)
      case Success(value) =>
        if value == 1 then Right(me)
        else handleExceptionMessage(new Exception("Incorrect login or password"))
    }


  /**
   * no modify
   * @param me
   * @param chat
   * @return
   */
  def deleteMeFromChat(me: User, chat: Chat): Either[QueryErrors, Chat] =
    numOfChatUsers(chat) match {
      case Left(queryErrors: QueryErrors) => Left(queryErrors)
      case Right(value) =>
        if value < 0 then
          Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.DataProcessingError))))
        else if !chat.groupChat then // cannot remove user from no group chat
          Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UnsupportedOperation))))
        else // we process group chat
          Using(connection.prepareStatement("DELETE FROM users_chats WHERE chat_id = ? AND user_id = ?")) {
            (statement: PreparedStatement) =>
              statement.setString(1, chat.chatId)
              statement.setObject(2, me.userId)
              statement.executeUpdate()
          } match {
            case Failure(ex) => handleExceptionMessage(ex)
            case Success(value) =>
              if value == 1 then Right(chat)
              else Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.DataProcessingError))))
          }
    }


  private def numOfChatUsers(chat: Chat): Either[QueryErrors, Int] =
    Using(connection.prepareStatement("SELECT * FROM users_chats WHERE chat_id = ?")) {
      (statement: PreparedStatement) =>
        statement.setString(1, chat.chatId)
        Using(statement.executeQuery()) {
          (resultSet: ResultSet) =>
            var numUsers = 0
            while (resultSet.next()) numUsers += 1
            if numUsers == 0 then // this may mean that chat id is incorrect so no users found
              Left(QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.DataProcessingError))))
            else Right(numUsers)
        } match {
          case Failure(ex) => throw ex
          case Success(either) => either
        }
    } match {
      case Failure(ex)     => handleExceptionMessage(ex)
      case Success(either) => either
    }



  private def handleExceptionMessage[A](ex: Throwable): Either[QueryErrors, A] =
    if ex.getMessage == "FATAL: terminating connection due to administrator command"
      || ex.getMessage == "This connection has been closed."
      || ex.getMessage == "An I/O error occurred while sending to the backend." then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.NoDbConnection))))
    else if ex.getMessage.toLowerCase.contains("timeout") then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.TimeOutDBError))))
    else if ex.getMessage == "Incorrect login or password" then
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.IncorrectLoginOrPassword))))
    else if ex.getMessage.contains("duplicate key value violates unique constraint")
      || ex.getMessage == "Data processing error."
      || ex.getMessage.contains("violates foreign key constraint") then
      Left(QueryErrors(List(QueryError( QueryErrorType.FATAL_ERROR, QueryErrorMessage.DataProcessingError))))
    else
      Left(QueryErrors(List(QueryError(QueryErrorType.FATAL_ERROR, QueryErrorMessage.UndefinedError(ex.getMessage)))))

