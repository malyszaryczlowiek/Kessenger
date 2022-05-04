package com.github.malyszaryczlowiek
package integrationTests.db

import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrorMessage, QueryErrors}
import com.github.malyszaryczlowiek.db.*
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.messages.Chat
import com.github.malyszaryczlowiek.util.PasswordConverter

import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID
import scala.util.{Failure, Success}
import sys.process.*


/**
 * Integration tests for DB.
 * Each test runs on separate freshly build DB docker container
 *
 * NOTE:
 * watch out if you stop running test before its end. If you do so,
 * you have started docker container which you must stop manually before
 * starting any next test. Otherwise next started test will fail due to
 * << docker container name collision >>
 *
 */
class DatabaseTests extends munit.FunSuite:

  /**
   * we must give some time to initialize container, because
   * docker container is started as demon and starting script returns immediately
   */
  val waitingTimeMS = 3000
  val pathToScripts = "./src/test/scala/integrationTests/db"
  var switchOffDbEarlier = false
  var fooTime: LocalDateTime = _

  /**
   * Before all integration tests we must set database
   * generating and removing scripts executable. Even if they so.
   */
  override def beforeAll(): Unit =
    val executableStartTest = s"chmod +x $pathToScripts/startTestDB".!!
    val executableStopTest = s"chmod +x $pathToScripts/stopTestDB".!!
    fooTime = LocalDateTime.now()
    super.beforeAll()

  /**
   * Before Each test we need to start up new DB, Wait for initialization
   * recreate connection to db.
   */
  override def beforeEach(context: BeforeEach): Unit =
    switchOffDbEarlier = false
    val outputOfDockerStarting = s"./$pathToScripts/startTestDB".!!
    Thread.sleep(waitingTimeMS)
    println(outputOfDockerStarting)
    println("Database prepared...")
    ExternalDB.recreateConnection()


  /**
   * After Each test we close used connection, and if required
   * switch off and delete db container as well.
   */
  override def afterEach(context: AfterEach): Unit =
    ExternalDB.closeConnection() match {
      case Failure(ex) => println(ex.getMessage)
      case Success(value) => println("connection closed correctly")
    }
    if !switchOffDbEarlier then
      val outputOfDockerStopping = s"./$pathToScripts/stopTestDB".!!
      println(outputOfDockerStopping)


  def switchOffDbManually(): Unit =
    val outputOfDockerStopping = s"./$pathToScripts/stopTestDB".!!
    println(outputOfDockerStopping)
    switchOffDbEarlier = true






  // searching user in db

  /**
   * Searching user by login when user exists in DB
   */
  test("Searching user by login when user exists in DB") {
    ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        assert(false, s"""Assertion error, should find user in db""".stripMargin)
      case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
    }
  }

  /**
   * Searching user by login when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {
    ExternalDB.findUser("NonExistingLogin") match {
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.size == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.UserNotFound("NonExistingLogin"),
          s"""Assertion error, should get one error message of:
             |=> ${QueryErrorMessage.UserNotFound("NonExistingLogin")}""".stripMargin)
      case Right(dbUser: User) =>
        assert(false,
          s"""Assertion error, function should return Query error:
             |=> \"User not found.\"
             |but returned not existing user:
             |=> ${dbUser.login}""".stripMargin)
    }
  }

  /**
   * Searching user by login when DB is down
   */
  test(" Searching user by login when DB is down.") {

    switchOffDbManually()

    ExternalDB.findUser("NonExistingLogin") match {
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"""Assertion error, should get error message:
             |=> ${QueryErrorMessage.UserNotFound("NonExistingLogin")}""".stripMargin)
      case Right(dbUser: User) =>
        assert(false,
          s"""Assertion error, function should return Query error:
             |=> ${QueryErrorMessage.UserNotFound("NonExistingLogin")}""".stripMargin)
    }
  }



  // testing of creation/insertions of new user

  /**
   * Testing user insertion not present in db
   */
  test("Testing user insertion"){

    val login = "Wojtas"

    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        throw new Exception("Password converter failed")
      case Right(pass) =>
        ExternalDB.createUser(login, pass)  match {
          case Left(queryErrors: QueryErrors) =>
            assert(false, s"user should be added normally.")
          case Right(dbUser: User) =>
            assert(dbUser.login == login, s"Returned user's login does not match inserted. Returned: ${dbUser.login}")
        }
    }
  }

  /**
   * Testing user insertion when user with this login exists now in DB
   */
  test("Testing user insertion with login present in DB"){
    val name = "Walo"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        throw new Exception("Password converter failed")
      case Right(pass) =>
        ExternalDB.createUser(name, pass) match {
          case Left(queryErrors: QueryErrors) =>
            assert( queryErrors.listOfErrors.nonEmpty
              && queryErrors.listOfErrors.length == 1
              && queryErrors.listOfErrors.head.description == QueryErrorMessage.LoginTaken,
              s"Result error does not contain: ${QueryErrorMessage.LoginTaken}")
          case Right(dbUser) => assert(false, "This test should return Left(QueryError)")
        }
    }
  }


  /**
   * Testing user insertion when DB is not available,
   * here we switch off DB container before inserting user to them.
   */
  test("Testing user insertion with login present in DB when DB is not available") {

    switchOffDbManually() // here we switch off docker container

    val name = "Walo"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        throw new Exception("Password converter failed")
      case Right(pass) =>
        ExternalDB.createUser(name, pass) match {
          case Left(queryErrors: QueryErrors) =>
            // Server Error: FATAL: terminating connection due to administrator command
            assert( queryErrors.listOfErrors.nonEmpty
              && queryErrors.listOfErrors.length == 1
              && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
              s"""Returned error message
                  |=> \"${queryErrors.listOfErrors.head.description}
                  |is other than expected
                  |\"${QueryErrorMessage.NoDbConnection}\"""".stripMargin)
          case Right(dbUser) =>
            assert(false,
              s"""Test should not return User object,
                |but returned QueryError object with message:
                |=> \"${QueryErrorMessage.NoDbConnection}\"""".stripMargin)
        }
    }
  }



  // chat creation


  /**
   * Testing that there is not possible to create
   * chat for single user.
   *
   * In reality it is unit test, because if list.size < 2
   * there is no call to db.
   */
  test("Impossible chat creation for single user.") {

    val user: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    ExternalDB.createChat(List(user), "Some chat name") match {
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.AtLeastTwoUsers,
          s"""Wrong error message: ${QueryErrorMessage.AtLeastTwoUsers}""".stripMargin
        )
      case _ => assert(false,
        s"""Method should return Query error with message:
           |=>\"To create new chat, you have to select two users at least.\"""".stripMargin)
    }
  }


  /**
   * Create chat when both users information are taken from DB
   */
  test("chat creation when both users are taken from DB") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) =>
        assert(chat.chatName == chatName, s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
      case Left(queryErrors: QueryErrors) =>
        assert(false, "Some error returned.")
    }
  }


  /**
   * due to DB constraint in user_chat to use only user_id present in
   * users table db returns exception and test fails.
   */
  test("Testing chat creation using two users which one of them not exists in DB ") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2 = User(UUID.randomUUID(), "NonExistingInDb")

    ExternalDB.createChat(List(user2, user1), "Any chat name") match {
      case Left(queryErrors: QueryErrors)  =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.UserNotFound("NonExistingInDb"),
          s"""Wrong Error message:
             |=> ${QueryErrorMessage.UserNotFound("NonExistingInDb")}
             |should return following one:
             |=> \"${user2.login} not found.\"""".stripMargin)
      case Right(_) => assert(false, "Function should return Query error. ")
    }
  }

  /**
   * due to DB constraint in user_chat to use only user_id present in
   * users table db returns exception and test fails.
   */
  test("Testing chat creation using three users which two of them not exists in DB ") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2 = User(UUID.randomUUID(), "NonExistingInDb")
    val user3 = User(UUID.randomUUID(), "NonExistingInDb2")

    ExternalDB.createChat(List(user1, user2, user3), "Any chat name") match {
      case Left(queryErrors: QueryErrors)  =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 2
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.UserNotFound("NonExistingInDb")
          && queryErrors.listOfErrors(1).description == QueryErrorMessage.UserNotFound("NonExistingInDb2"),
          s"""Wrong Error message:
             |=> ${QueryErrorMessage.UserNotFound("NonExistingInDb")}
             |should return following one:
             |=> \"${user2.login} not found.\"""".stripMargin)
      case Right(_) => assert(false, "Function should return Query error. ")
    }
  }

  /**
   * Trying to create chat when DB is unavailable
   */
  test("Trying to create new chat when DB is unavailable") {
    val user1: User = User(UUID.randomUUID(), "pass1")
    val user2: User = User(UUID.randomUUID(), "pass2")
    val user3: User = User(UUID.randomUUID(), "pass3")

    val chatName: ChatName = "ChatName"

    switchOffDbManually() // IMPORTANT we need lost connection to db

    ExternalDB.createChat(List(user1, user2, user3), chatName) match {
      case Right(chat: Chat) =>
        assert(false, s"""Assertion error, should return
                  |=> \"Connection to DB lost. Try again later.\"
                  |but returned:
                  |=> Chat object: $chat""".stripMargin)
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
        s"Non Empty: ${queryErrors.listOfErrors.nonEmpty}; Size = ${queryErrors.listOfErrors.length};" +
          s" Head description: ${queryErrors.listOfErrors.head.description}")
    }
  } // An I/O error occurred while sending to the backend.




  /**
   * Searching users chats.
   */
  test("Searching users chats") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    val createdChat: Chat = ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) => chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception("Method should return chat object.")
    }

    ExternalDB.findUsersChats(user1) match {
      case Right(map: Map[Chat, List[User]]) => // : Map[Chat, List[User]]
        assert(map.nonEmpty
          && map.size == 1
          && ( map.head == (createdChat, List(user1, user2))
          || map.head == (createdChat, List(user2, user1)) ), s"Should return only created chat. ${map.head}")
      case Left(queryErrors: QueryErrors) =>
        assert(false, "Method should return Map[Chat, List[User]] object," +
        s" but returned ${queryErrors.listOfErrors.head.description}")
    }

    ExternalDB.findUsersChats(user2) match {
      case Right(map: Map[Chat, List[User]]) => // : Map[Chat, List[User]]
        assert(map.nonEmpty
          && map.size == 1
          && ( map.head == (createdChat, List(user1, user2))
          || map.head == (createdChat, List(user2, user1)) ), "Should return only created chat.")
      case Left(queryErrors: QueryErrors) => assert(false, "Method should return Map[Chat, List[User]] object," +
        s" but returned ${queryErrors.listOfErrors.head.description}")
    }
  }

  /**
   * Searching users chat when user has not any chat.
   */
  test("Searching users chat when user has not any chat.") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    ExternalDB.findUsersChats(user1) match {
      case Right(map: Map[Chat, List[User]]) =>
        assert(map.isEmpty, s"Returned map should be empty, but was: $map")
      case Left(queryErrors: QueryErrors) =>
        assert(false, "Method should return Map[Chat, List[User]] object," +
          s" but returned ${queryErrors.listOfErrors.head.description}")
    }
  }

  /**
   * Searching users chats when DB is down.
   */
  test("Searching users chats when DB is down.") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    val createdChat: Chat = ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) => chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception("Method should return chat object.")
    }

    switchOffDbManually()

    ExternalDB.findUsersChats(user1) match {
      case Right(map: Map[Chat, List[User]]) => // : Map[Chat, List[User]]
        assert(false, s"Should return query Error but returned: ${map.head}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          "Method should return Map[Chat, List[User]] object," +
            s" but returned ${queryErrors.listOfErrors.head.description}")
    }

    ExternalDB.findUsersChats(user2) match {
      case Right(map: Map[Chat, List[User]]) => // : Map[Chat, List[User]]
        assert(false, s"Should return query Error but returned: ${map.head}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          "Method should return Map[Chat, List[User]] object," +
            s" but returned ${queryErrors.listOfErrors.head.description}")
    }
  }


  // testing password update


  /**
   * Testing update user's password when user is present in DB
   */
  test("Testing update user's password when user is present in DB") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val wojtas = ExternalDB.createUser("Wojtas", oldPass) match {
      case Left(_) =>
        throw new Exception("User should be found in Db and user object should be returned. ")
        nullUser
      case Right(dbUser) =>
        assert(dbUser.login == "Wojtas", "Not the same login")
        dbUser
    }

    val newPass: Password = PasswordConverter.convert("newPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }


    ExternalDB.updateUsersPassword(wojtas, oldPass, newPass) match {
      case Right(user) =>
        assert(user.login == "Wojtas", "not matching login")
      case Left(_) => assert( false, "should change password correctly")
    }
  }


  /**
   * Testing update user's password when user is present in DB,
   * but old password is incorrect.
   */
  test("Testing update user's password when user is present in DB, but old password is incorrect") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val wojtas = ExternalDB.createUser("Wojtas", oldPass) match {
      case Left(_) =>
        throw new Exception("Method should return user Object.")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Incorrect login returned.")
        dbUser
    }

    val incorrectOldPass: Password = PasswordConverter.convert("incorrectOldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val newPass: Password = PasswordConverter.convert("newPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }


    ExternalDB.updateUsersPassword(wojtas, incorrectOldPass, newPass) match {
      case Right(user) =>
        assert(false, "Method should return QueryErrors object")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.IncorrectLoginOrPassword,
          s"Test should return error message: ${QueryErrorMessage.IncorrectLoginOrPassword}")
    }
  }


  /**
   * Testing update user's password when user is absent in DB
   */
  test("Testing update user's password when user is absent in DB") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }


    val incorrectOldPass: Password = PasswordConverter.convert("incorrectOldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val newPass: Password = PasswordConverter.convert("newPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }


    ExternalDB.updateUsersPassword(nullUser, incorrectOldPass, newPass) match {
      case Right(user) =>
        assert(false, "Method should return QueryErrors object")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.IncorrectLoginOrPassword,
          s"Test should return error message: ${QueryErrorMessage.IncorrectLoginOrPassword}")
    }
  }


  /**
   * Testing update user's password when DB is unavailable
   */
  test("Testing update user's password when DB is down") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }


    val incorrectOldPass: Password = PasswordConverter.convert("incorrectOldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val newPass: Password = PasswordConverter.convert("newPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    switchOffDbManually()

    ExternalDB.updateUsersPassword(nullUser, incorrectOldPass, newPass) match {
      case Right(user) =>
        assert(false, "Method should return QueryErrors object")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"Test should return error message: ${QueryErrorMessage.NoDbConnection}")
    }
  }



  // Testing login update

  test("Testing update user's login when user is present in DB and login is changeable.") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val wojtas = ExternalDB.createUser("Wojtas", oldPass) match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val newLogin: Login = "Wojtasso"

    ExternalDB.updateMyLogin(wojtas, newLogin, oldPass) match {
      case Right(user) => assert(user.login == "Wojtasso", "not matching login")
      case Left(_)     => assert( false, "should change password correctly")
    }
  }


  test("Testing update user's login when user is present in DB and login is taken.") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val wojtas = ExternalDB.createUser("Wojtas", oldPass) match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val newLogin: Login = "Spejson" //  this login is currently taken.

    ExternalDB.updateMyLogin(wojtas, newLogin, oldPass) match {
      case Right(_) => assert(false, s"Method should return QueryErrors object with message: ${QueryErrorMessage.LoginTaken}")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.LoginTaken,
          s"Method should return: ${QueryErrorMessage.LoginTaken}")
    }
  }


  test("Testing update user's login when user is present in DB but DB is not available.") {

    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val oldPass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    val wojtas = ExternalDB.createUser("Wojtas", oldPass) match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val newLogin: Login = "NewLogin" //  this login is currently taken.

    switchOffDbManually()

    ExternalDB.updateMyLogin(wojtas, newLogin, oldPass) match {
      case Right(_) => assert(false, s"Method should return QueryErrors object with message: ${QueryErrorMessage.NoDbConnection}")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"Method should return: ${QueryErrorMessage.NoDbConnection}")
    }
  }


  // updates of chat name in DB

  /**
   * Testing of updating of chat name
   */
  test("Testing of updating of chat name") {

    // first we take two users

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"


    // then we create chat

    val chat: Chat = ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) =>
        if chat.chatName != chatName then
          throw new Exception(s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
        chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"Method should return Chat object.")
        Chat("null", "NullChat name", false, 0, fooTime)
    }

    // finally we try to rename it.

    val newChatName: ChatName = "Ole ole ale bieda w oczy kole"

    ExternalDB.updateChatName(chat, newChatName) match {
      case Right(chatName) =>
        assert(chatName == newChatName, s"Chat name does not match.")
      case Left(_) =>
        assert(false, s"Method should return new chat name.")
    }
  }



  /**
   *  Testing of updating of chat name when chat does not exist in DB
   */
  test("Testing of updating of chat name when chat does not exist in DB") {

    val newChatName: ChatName = "Ole ole ale bieda w oczy kole"
    val fakeChat = Chat("ChatId", "Old chat name", false, 0, fooTime)

    ExternalDB.updateChatName(fakeChat, newChatName) match {
      case Right(_) =>
        assert(false, s"Chat name does not match.")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.ChatDoesNotExist(fakeChat.chatName),
          s"Method should return ${QueryErrorMessage.ChatDoesNotExist(fakeChat.chatName)}.")
    }
  }


  /**
   * Testing of updating of chat name when DB is down
   */
  test("Testing of updating of chat name when DB is down") {

    // first we take two users

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    // then we create chat
    val chat: Chat = ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) =>
        if chat.chatName != chatName then
          throw new Exception(s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
        chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"Method should return Chat object.")
        Chat("null", "NullChat name", false, 0, fooTime)
    }

    // new name
    val newChatName: ChatName = "Ole ole ale bieda w oczy kole"

    // switch of db
    switchOffDbManually()

    // finally we try to rename it.
    ExternalDB.updateChatName(chat, newChatName) match {
      case Right(_) =>
        assert(false, s"Method should return QueryErrors object not ChatName.")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"Method should return ${QueryErrorMessage.NoDbConnection}.")
    }
  }





  // adding new users to chat

  /**
   *  Add user to existing group chat
   */
  test("Add user to existing group chat") {
    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val wojtas = ExternalDB.createUser("Wojtas", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    // then we create chat
    val chat: Chat = ExternalDB.createChat(List(user1, user2, wojtas), chatName) match {
      case Right(chat: Chat) =>
        if chat.chatName != chatName then
          throw new Exception(s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
        chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"Method should return Chat object.")
        Chat("null", "NullChat name", false, 0, fooTime)
    }

    val solaris = ExternalDB.createUser("solaris", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "solaris" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    ExternalDB.addNewUsersToChat(List(solaris), chat) match {
      case Right(c) =>
        assert(c == chat, s"not matching chat")
      case Left(_) =>
        assert(false, s"method should return chat object")
    }
  }

  /**
   * Try to add user to non existing chat
   */
  test("Try to add user to non existing chat") {

    val fakeChat = Chat("chat-id", "chat-name", true, 0, fooTime)

    val solaris = ExternalDB.createUser("solaris", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "solaris" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    ExternalDB.addNewUsersToChat(List(solaris), fakeChat) match {
      case Right(_) =>
        assert(false, s"Method should return ${QueryErrorMessage.DataProcessingError}")
      case Left(queryErrors: QueryErrors) =>
        println(queryErrors)
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.DataProcessingError
          , s"Method should return ${QueryErrorMessage.DataProcessingError}")
    }
  }

  /**
   * Try to add empty list of users
   */
  test("Try to add empty list of users") {
    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val wojtas = ExternalDB.createUser("Wojtas", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    // then we create chat
    val chat: Chat = ExternalDB.createChat(List(user1, user2, wojtas), chatName) match {
      case Right(chat: Chat) =>
        if chat.chatName != chatName then
          throw new Exception(s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
        chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"Method should return Chat object.")
        Chat("null", "NullChat name", false, 0, fooTime)
    }

    ExternalDB.addNewUsersToChat(List.empty[User], chat) match {
      case Right(_) =>
        assert(false, s"Method should return ${QueryErrorMessage.NoUserSelected}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoUserSelected
          , s"Method should return ${QueryErrorMessage.NoUserSelected}")
    }
  }



  /**
   * Try to add user to chat when db is down
   */
  test("Try to add user to chat when db is down") {
    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception(s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val wojtas = ExternalDB.createUser("Wojtas", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    // then we create chat
    val chat: Chat = ExternalDB.createChat(List(user1, user2, wojtas), chatName) match {
      case Right(chat: Chat) =>
        if chat.chatName != chatName then
          throw new Exception(s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
        chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"Method should return Chat object.")
        Chat("null", "NullChat name", false, 0, fooTime)
    }

    val solaris = ExternalDB.createUser("solaris", "pass") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "NullLogin")
      case Right(dbUser) =>
        if dbUser.login != "solaris" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    switchOffDbManually()

    ExternalDB.addNewUsersToChat(List(solaris), chat) match {
      case Right(_) =>
        assert(false, s"Method should return ${QueryErrorMessage.NoDbConnection}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection
          , s"Method should return ${QueryErrorMessage.NoDbConnection}")
    }
  }



  // DELETING from DB
  /**
   *
   */
  test("Testing deleting user from db permanently") {
    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val pass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    // create user and save them in db
    val wojtas = ExternalDB.createUser("Wojtas", pass) match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    // delete user
    ExternalDB.deleteMyAccountPermanently(wojtas, pass) match {
      case Right(user) =>
        assert(user == wojtas, s"Returned user not match")
      case Left(queryErrors: QueryErrors) =>
        assert(false, s"Method should return User object")
    }

    // check if user is deleted
    ExternalDB.findUser(wojtas) match {
      case Right(_) =>
        assert(false, s"User should not be found after deleting.")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.UserNotFound(wojtas.login)
          , s"Method should return ${QueryErrorMessage.UserNotFound(wojtas.login)}"
        )
    }
  }


  /**
   *
   */
  test("Testing deleting user from db permanently but with wrong password") {
    val nullUser = User(UUID.randomUUID(), "NullLogin")

    val pass: Password = PasswordConverter.convert("oldPass") match {
      case Left(value) =>
        throw new Exception("Password conversion failed")
        "Null password"
      case Right(value) => value
    }

    // create user and save them in db
    val wojtas = ExternalDB.createUser("Wojtas", pass) match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        nullUser
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    // delete user
    ExternalDB.deleteMyAccountPermanently(wojtas, "Wrong Password") match {
      case Right(user) =>
        assert(false, s"Method should returned ${QueryErrorMessage.IncorrectLoginOrPassword}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.IncorrectLoginOrPassword,
          s"Method should return ${QueryErrorMessage.IncorrectLoginOrPassword}")
    }

    // check if user is deleted
    ExternalDB.findUser(wojtas) match {
      case Right(user) =>
        if user != wojtas then throw new Exception(s"Method should find not deleted user.")
      case Left(_) => throw new Exception("Method should return not deleted user.")
    }
  }


  /**
   *
   */
  test("Testing deleting user from db permanently but user does not exists in db") {

    // create non existing user
    val nullUser = User(UUID.randomUUID(), "NullLogin")

    // delete user
    ExternalDB.deleteMyAccountPermanently(nullUser, "Wrong Password") match {
      case Right(user) =>
        assert(false, s"Method should returned ${QueryErrorMessage.IncorrectLoginOrPassword}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.IncorrectLoginOrPassword,
          s"Method should return ${QueryErrorMessage.IncorrectLoginOrPassword}")
    }
  }


  /**
   *
   */
  test("Testing deleting user from db permanently but db is down") {

    // create non existing user
    val nullUser = User(UUID.randomUUID(), "NullLogin")

    // db down
    switchOffDbManually()

    // delete user
    ExternalDB.deleteMyAccountPermanently(nullUser, "Wrong Password") match {
      case Right(_) =>
        assert(false, s"Method should returned ${QueryErrorMessage.NoDbConnection}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"Method should return ${QueryErrorMessage.NoDbConnection}")
    }
  }


  // Deleting user from chat

  /**
   *
   */
  test("Delete user from chat - Not possible if chat is not group (has two users)") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    val createdChat: Chat = ExternalDB.createChat(List(user1, user2), chatName) match {
      case Right(chat: Chat) =>
        chat
        //assert(chat.chatName == chatName, s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
      case Left(queryErrors: QueryErrors) =>
        throw new Exception("method should return Chat object")
    }

    ExternalDB.deleteMeFromChat(user1, createdChat) match {
      case Right(_) => assert(false, s"method should return ${QueryErrorMessage.UnsupportedOperation}")
      case Left(queryErrors: QueryErrors) =>
        assert( queryErrors.listOfErrors.nonEmpty
        && queryErrors.listOfErrors.length == 1
        && queryErrors.listOfErrors.head.description == QueryErrorMessage.UnsupportedOperation,
          s"Method should return ${QueryErrorMessage.UnsupportedOperation}")
    }
  }

  /**
   *
   */
  test("Delete user from chat - Possible for group chats (users more than two)") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    // create user and save them in db
    val wojtas = ExternalDB.createUser("Wojtas", "ddd") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    val createdChat: Chat = ExternalDB.createChat(List(user1, user2, wojtas), chatName) match {
      case Right(chat: Chat) => chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"method should return Chat object, but returned ${queryErrors.listOfErrors.head.description}")
    }

    ExternalDB.deleteMeFromChat(user1, createdChat) match {
      case Right(chat) =>
        assert(chat == createdChat, s"method should return $createdChat")
      case Left(_) => assert(false, s"Method should return $createdChat")
    }
  }

  /**
   *
   */
  test("Delete user from chat when db is down") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    // create user and save them in db
    val wojtas = ExternalDB.createUser("Wojtas", "ddd") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    val createdChat: Chat = ExternalDB.createChat(List(user1, user2, wojtas), chatName) match {
      case Right(chat: Chat) => chat
      case Left(queryErrors: QueryErrors) =>
        throw new Exception(s"method should return Chat object, but returned ${queryErrors.listOfErrors.head.description}")
    }

    switchOffDbManually()

    ExternalDB.deleteMeFromChat(user1, createdChat) match {
      case Right(_) =>
        assert(false, s"Method should return ${QueryErrorMessage.NoDbConnection}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
          s"Method should return ${QueryErrorMessage.NoDbConnection}")
    }
  }

  /**
   *
   */
  test("Delete user from no existing chat") {

    val user1: User = ExternalDB.findUser("Walo") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = ExternalDB.findUser("Spejson") match {
      case Left(_) =>
        throw new Exception("Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    // create user and save them in db
    val wojtas = ExternalDB.createUser("Wojtas", "ddd") match {
      case Left(_) =>
        throw new Exception("Assertion error, should find user in db")
        User(UUID.randomUUID(), "")
      case Right(dbUser) =>
        if dbUser.login != "Wojtas" then
          throw new Exception("Returned login is not matching.")
        dbUser
    }

    val chatName: ChatName = "Walo-Spejson-wojtas"

    val fakeChat = Chat("chat-id", "chat-name", false, 0, fooTime)

    ExternalDB.deleteMeFromChat(user1, fakeChat) match {
      case Right(_) =>
        assert(false, s"Method should return ${QueryErrorMessage.DataProcessingError}")
      case Left(queryErrors: QueryErrors) =>
        assert(queryErrors.listOfErrors.nonEmpty
          && queryErrors.listOfErrors.length == 1
          && queryErrors.listOfErrors.head.description == QueryErrorMessage.DataProcessingError,
          s"Method should return ${QueryErrorMessage.DataProcessingError}")
    }
  }

































