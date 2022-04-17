package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID, generateChatId}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLType, Statement}
import java.util.{Properties, UUID}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try, Using}

class ExternalDB extends DataBase: // [A <: Queryable](statement: A)

  var connection: Connection = ExternalDB.getConnection

  def createUser(login: Login, pass: Password): Either[QueryError,User] =
    Using(connection.prepareStatement("INSERT INTO users (login, pass)  VALUES (?, ?)")) {
      (statement: PreparedStatement) => // , Statement.RETURN_GENERATED_KEYS
        statement.setString(1, login)
        statement.setString(2, pass)
        val affectedRows: Int  = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          findUser(login)
        else
          Left( QueryError(s"Oooopss... Some Undefined Error... Cannot create user.") )
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

  /**
   * Chat creation
   * @param chatId
   * @param chatName
   * @return
   */
  def createChat(users: List[User], chatName: ChatName): Either[QueryError,Chat] =
    // TODO search users by calling in separate threads.
    val errors = users.map[Either[QueryError,User]](user =>
      findUser(user) match {
        case l @ Left(qE: QueryError) =>
          if qE.description == "User not found." then
            Left( QueryError(s"${user.login} not found.") )
          else
            l
        case Right(user: User) =>
          Right(user)
      }
    ).filter( either => // we take only errors
      either match {
        case Left(value) => true
        case Right(value) => false
      }
    )

    if errors.nonEmpty then
      val error: String = errors.foldLeft[String]("")(
        (s, either) =>
          either match {
            case Left(qe) => s"$s${qe.description}\n"
            case _ => ""
          }
        )
      val errorMessage = error.substring(0,error.length-1) // we remove last new line sign \n
      Left( QueryError(errorMessage) )
    else
      val listSize = users.length
      if listSize < 2 then
        Left(QueryError("To create new chat, you have to select two users at least."))
      else if listSize == 2 then
        val chatId: ChatId = Domain.generateChatId(users.head.userId, users(1).userId)
        insertChatAndChatUsers(users, chatId, chatName)
      else // for more than two users we generate random chat_id
        val chatId: ChatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
        insertChatAndChatUsers(users, chatId, chatName)
  end createChat


  private def insertChatAndChatUsers(user: List[User], chatId: ChatId, chatName: ChatName): Either[QueryError, Chat] =
    insertChat(chatId, chatName) match {
      case left @ Left(_) => left
      case Right(chat)    => assignUsersToChat(user, chat)
    }

  private def insertChat(chatId: ChatId, chatName: ChatName): Either[QueryError, Chat] =
    Using(connection.prepareStatement("INSERT INTO chats(chat_id, chat_name) VALUES (?,?)")) {
      (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
        statement.setString(1, chatId)
        statement.setString(2, chatName)
        val affectedRows = statement.executeUpdate()
        connection.commit()
        if affectedRows == 1 then
          Right( Chat(chatId, chatName) )
        else
          Left(QueryError("Oooppppsss some error with chat creation"))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else if ex.getMessage.contains("duplicate key value violates unique constraint") then // very impossible but chat_id duplication.
          Left( QueryError( s"Sorry Login is taken, try with another one.") )
        else
          Left( QueryError(ex.getMessage) )
      case Success(either) => either
    }

  private def assignUsersToChat(users: List[User], chat: Chat): Either[QueryError, Chat] =
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
          Left( QueryError("Server Error, cannot add user to chat.") )
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        //else if ex.getMessage.contains("duplicate key value violates unique constraint") then // very impossible but chat_id duplication.
          //Left( QueryError( s"Sorry Login is taken, try with another one.") )
        else if ex.getMessage.contains("was aborted: ERROR: insert or update on table \"users_chats\" violates foreign key constraint") then
          Left( QueryError( s"Ooopps... Error occurred. One or more users not found in system and not added to chat.") )
        else
          Left( QueryError(ex.getMessage) )
      case Success(either) => either
    }



  def findUser(user: User): Either[QueryError, User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE user_id=? AND login=?" ) ) {
      (statement: PreparedStatement) =>
        statement.setObject(1, user.userId)
        statement.setString(2, user.login)
        val resultSet = statement.executeQuery()
        if resultSet.next() then
          val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
          val login: Login   = resultSet.getString("login")
          resultSet.close()
          Right(User(userId, login))
        else
          resultSet.close()
          Left(QueryError("User not found."))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) ) // other errors
      case Success(either) => either
    }



  def findUser(login: Login): Either[QueryError,User] =
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
        Left(QueryError("User not found."))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) ) // other errors
      case Success(either) => either
    }


  def findUser(userId: UserID): Either[QueryError,User] =
    Using ( connection.prepareStatement( "SELECT user_id, login FROM users WHERE user_id = ?" ) ) {
      (statement: PreparedStatement) => //, Statement.RETURN_GENERATED_KEYS
        statement.setObject(1,userId)
        Using( statement.executeQuery() ) {
          (resultSet: ResultSet) =>
            if resultSet.next() then
              val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
              val login: Login = resultSet.getString("login")
              //resultSet.close() // resultSet is closed by Using()
              //statement.close() // statement is closed by Using()
              Right(User(userId, login))
            else
              //resultSet.close()
              //statement.close()
              Left(QueryError("User not found."))
        } match {
          case Failure(ex) =>
            Left( QueryError(ex.getMessage) )
          case Success(either) => either
        }
//        val resultSet = statement.executeQuery()
//        if resultSet.next() then
//          val userId: UserID = resultSet.getObject[UUID]("user_id", classOf[UUID])
//          val login: Login   = resultSet.getString("login")
//          resultSet.close() // TODO do naprawy ????
//          statement.close()
//          Right(User(userId, login))
//        else
//          resultSet.close()
//          statement.close()
//          Left(QueryError("User not found."))
    } match {
      case Failure(ex) =>
        if ex.getMessage == "FATAL: terminating connection due to administrator command" then // no connection to db
          Left( QueryError( s"Connection to DB lost. Try again later." ) )
        else
          Left( QueryError(ex.getMessage) ) // other errors
      case Success(either) => either
    }

  def findUsersChats(user: User): Either[QueryError,Seq[Chat]] =
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

  def findUsersChats(userId: UserID): Either[QueryError,Seq[Chat]] = ???
  def findUsersChats(login: Login): Either[QueryError,Seq[Chat]] = ???



  def updateUsersPassword(user: User, pass: Password): Either[QueryError,User] = ???
  def updateChatName(chatId: ChatId, newName: ChatName): Either[QueryError,ChatName] = ???

  def addUsersToChat(userIds: List[UserID], chatId: ChatId): List[Either[QueryError,UserID]] =
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
            Left( QueryError("Undefined Error") )
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
    )




  // add user to  existing chat
  // add user to chat


  def deleteUserPermanently(user: User): Either[QueryError,User] = ???
  def deleteUserPermanently(userId: UserID): Either[QueryError,User] = ???
  def deleteUserFromChat(chatId: ChatId, userID: UserID): Either[QueryError,User] = ???
  def deleteChat(chatId: ChatId): Either[QueryError,Chat] = ???





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



//      Using ( connection.prepareStatement( "INSERT INTO chats(chat_id, chat_name) VALUES (?,?)" ) ) {
//        (statement: PreparedStatement) => // ,      Statement.RETURN_GENERATED_KEYS
//          statement.setString(1, chatId)
//          statement.setString(2, chatName)
//          val affectedRows = statement.executeUpdate()
//          connection.commit()
//          if affectedRows == 1 then
//            var resultChatId: ChatId = ""
//            var resultChatName = ""
//            val confirmationQuery = "SELECT chat_id, chat_name FROM chats WHERE chat_id = ? AND chat_name = ?"
//            Using(connection.prepareStatement( confirmationQuery )) {
//              (innerStatement: PreparedStatement) =>
//                innerStatement.setString(1, chatId)
//                innerStatement.setString(2, chatName)
//                val resultSet: ResultSet = innerStatement.executeQuery()
//                if resultSet.next() then
//                  resultChatId = resultSet.getString("chat_id")
//                  resultChatName = resultSet.getString("chat_name")
//                  resultSet.close()
//                  Right(Chat(chatId, chatName))
//                else
//                  resultSet.close()
//                  Left(QueryError("Created chat not found in DB"))
//            } match {
//              case Failure(ex) =>
//                Left( QueryError(ex.getMessage) )
//              case Success( either ) => either
//            }
//          else
//
//      } match {
//        case Failure(ex) =>
//
//        case Success(either) => either
//      }