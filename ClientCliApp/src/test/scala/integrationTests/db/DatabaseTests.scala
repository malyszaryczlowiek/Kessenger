package com.github.malyszaryczlowiek
package integrationTests.db

import com.github.malyszaryczlowiek.db.queries.{QueryErrors, QueryError, QueryErrorMessage}
import com.github.malyszaryczlowiek.db.*
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.domain.{Domain, PasswordConverter, User}
import com.github.malyszaryczlowiek.messages.Chat

import java.sql.Connection
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

  private var cd: ExternalDB = _

  /**
   * we must give some time to initialize container, because
   * docker container is started as demon and starting script returns immediately
   */
  val waitingTimeMS = 3000
  val pathToScripts = "./src/test/scala/integrationTests/db"
  var switchOffDbEarlier = false

  /**
   * Before all integration tests we must set database
   * generating and removing scripts executable. Even if they so.
   */
  override def beforeAll(): Unit =
    val executableStartTest = s"chmod +x ${pathToScripts}/startTestDB ".!!
    val executableStopTest = s"chmod +x ${pathToScripts}/stopTestDB".!!
    super.beforeAll()

  /**
   * Before Each test we need to start up new DB, Wait for initialization
   * recreate connection to db.
   * @param context
   */
  override def beforeEach(context: BeforeEach): Unit =
    switchOffDbEarlier = false
    val outputOfDockerStarting = s"./${pathToScripts}/startTestDB".!!
    Thread.sleep(waitingTimeMS)
    println(outputOfDockerStarting)
    println("Database prepared...")
    ExternalDB.recreateConnection()
    cd = new ExternalDB()

  /**
   * After Each test we close used connection, and if required
   * switch off and delete db container as well.
   * @param context
   */
  override def afterEach(context: AfterEach): Unit =
    ExternalDB.closeConnection() match {
      case Failure(ex) => println(ex.getMessage)
      case Success(value) => println("connection closed correctly")
    }
    if !switchOffDbEarlier then
      val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
      println(outputOfDockerStopping)


  def switchOffDbManually(): Unit =
    val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
    println(outputOfDockerStopping)
    switchOffDbEarlier = true






  // searching user in db

  /**
   * Searching user by login when user exists in DB
   */
  test("Searching user by login when user exists in DB") {
    cd.findUser("Spejson") match {
      case Left(_) =>
        assert(false, s"""Assertion error, should find user in db""".stripMargin)
      case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
    }
  }

  /**
   * Searching user by login when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {
    cd.findUser("NonExistingLogin") match {
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

    cd.findUser("NonExistingLogin") match {
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
   * TODO first insert user and in second step find them with
   *   and if number of results is other than 1 then test fails
   * Testing normal user insertion
   */
  test("Testing user insertion"){

    val login = "Wojtas"
    val randomUUID = UUID.randomUUID()
    val errorUser = User(randomUUID, "Null")

    val user: User = PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
        errorUser
      case Right(pass) =>
        cd.createUser(login, pass)  match {
          case Left(queryErrors: QueryErrors) =>
            assert(false,s"user should be added normally.")
            errorUser
          case Right(dbUser: User) =>
            assert(dbUser.login == login, s"Returned user's login does not match inserted. Returned: ${dbUser.login}")
            dbUser
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
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass) match {
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


    //  normally try to execute user insertion.
    val name = "Walo"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass) match {
          case Left(queryErrors: QueryErrors) =>
            // Server Error: FATAL: terminating connection due to administrator command
            assert( queryErrors.listOfErrors.nonEmpty
              && queryErrors.listOfErrors.length == 1
              && queryErrors.listOfErrors.head.description == QueryErrorMessage.NoDbConnection,
              s"""Returned error message
                  |=> \"${QueryErrorMessage.LoginTaken}
                  |is other than expected
                  |\"Connection to DB lost. Try again later.\"
                """)
          case Right(dbUser) =>
            assert(false,
              s"""Test should not return User object,
                |but returned QueryError object with message:
                |=> \"Connection to DB lost. Try again later.\"""".stripMargin)
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

    val user: User = cd.findUser("Walo") match {
      case Left(_) =>
        assert(false, s"Db call should return user, but returned Error")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    cd.createChat(List(user), "Some chat name") match {
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
   * TODO ----> rewrite the test for many users <----
   */
  test("chat creation when both users are taken from DB") {

    val user1: User = cd.findUser("Walo") match {
      case Left(_) =>
        //Thread.sleep(120_000)
        assert(false, s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = cd.findUser("Spejson") match {
      case Left(_) =>
        assert(false, s"Db call should return user")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatName: ChatName = "Walo-Spejson"

    cd.createChat(List(user1, user2), chatName) match {
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

    val user1: User = cd.findUser("Walo") match {
      case Left(_) =>
        assert(false, s"DB call should return user.")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2 = User(UUID.randomUUID(), "NonExistingInDb")

    cd.createChat(List(user1, user2), "Any chat name") match {
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

    val user1: User = cd.findUser("Walo") match {
      case Left(_) =>
        assert(false, s"DB call should return user.")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2 = User(UUID.randomUUID(), "NonExistingInDb")
    val user3 = User(UUID.randomUUID(), "NonExistingInDb2")

    cd.createChat(List(user1, user2, user3), "Any chat name") match {
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
   * TODO ----> rewrite the test for many users <----
   */
  test("Trying to create new chat when DB is unavailable") {
    val user1: User = User(UUID.randomUUID(), "pass1")
    val user2: User = User(UUID.randomUUID(), "pass2")
    val user3: User = User(UUID.randomUUID(), "pass3")

    val chatName: ChatName = "ChatName"

    switchOffDbManually() // IMPORTANT we need lost connection to db

    cd.createChat(List(user1, user2, user3, user1,user2), chatName) match {
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
          s" Head description: ${queryErrors.listOfErrors.tail.head.description}")
    }
  }



  // searching user's chats

  /**
   * TODO Searching user's chats by user's login when user exists in DB
   *   ----> rewrite the test <-----
   */
  test("Searching user's chats by his/her login when user exists in DB") {

    // inserting needed data to DB

//    val user1: User = cd.findUser("Walo") match {
//      case Left(queryError: QueryError) =>
//        assert(false, s"${queryError.description}")
//        User(UUID.randomUUID(), "")
//      case Right(user: User) => user
//    }
//
//    val user2: User = cd.findUser("Spejson") match {
//      case Left(queryError: QueryError) =>
//        assert(false, s"${queryError.description}")
//        User(UUID.randomUUID(), "")
//      case Right(user: User) => user
//    }
//
//    //val chatId: ChatId     = Domain.generateChatId(user1.userId, user2.userId)
//    val chatName: ChatName = "Walo-Spejson"
//
//    val chat: Chat = cd.createChat(List(user1, user2), chatName) match {
//      case Right(chat: Chat) => chat
//      case Left(queryError: QueryError) =>
//        assert(false, "Assertion error, Method should return Chat object")
//        Chat("Null", "Null")
//    }
//
//    val returnedChatId = chat.chatId


    //






  }

  /**
   * TODO Searching user's chats by user's login when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {

  }

  /**
   * TODO Searching user's chats by user's login when DB is down
   */
  test(" Searching user by login when DB is down.") {

  }


  /**
   * TODO Searching user's chats by userId when user exists in DB
   */
  test("Searching user by login when user exists in DB") {

  }

  /**
   * TODO Searching user's chats by userId when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {

  }

  /**
   * TODO Searching user's chats by userId when  DB is down
   */
  test(" Searching user by login when DB is down.") {

  }








  // testing updates to DB


  /**
   * TODO Testing update user's password when user is present in DB
   */

  test("Testing update user's password when user is present in DB") {

  }

  /**
   * TODO Testing update user's password when user is absent in DB
   */
  test("Testing update user's password when user is absent in DB") {

  }

  /**
   * TODO Testing update user's password when DB is down
   */
  test("Testing update user's password when DB is down.") {

  }


  // updates of chat name in DB

  /**
   * TODO Testing of updating of chat name
   */
  test("Testing of updating of chat name") {

  }


  /**
   * TODO Testing of updating of chat name when chat_id is now found in DB
   */
  test("Testing of updating of chat name") {

  }


  /**
   * TODO Testing of updating of chat name when chat_id does not exist in DB
   */
  test("Testing of updating of chat name when chat_id does not exist in DB") {

  }


  /**
   * TODO Testing of updating of chat name when DB is down
   */
  test("Testing of updating of chat name when DB is down") {

  }

  // adding user to chat

  /**
   * TODO Add user to existing chat
   */
  test("Add user to existing chat") {
    // TODO here implement
  }

  /**
   * TODO Try to add user to non existing chat
   */
  test("Try to add user to non existing chat") {

  }

  /**
   * TODO Try to add user to chat when db is down
   */
  test("Try to add user to chat when db is down") {

  }







  // DELETING

  test("Testing deleting user by user object permanently") {


  }









































//  test("generate Wojtas") {
//    val uuid = UUID.randomUUID()
//    println(uuid)
//  }



