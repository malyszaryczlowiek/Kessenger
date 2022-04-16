package com.github.malyszaryczlowiek
package integrationTests.db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.db.{ExternalDB, *}
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
      case Failure(exception) => println(exception.getMessage)
      case Success(value) => println("connection closed correctly")
    }
    if !switchOffDbEarlier then
      val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
      println(outputOfDockerStopping)


  def switchOffDbManually(): Unit =
    val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
    println(outputOfDockerStopping)
    switchOffDbEarlier = true


  // testing of creation/insertions

  /**
   * Testing normal user insertion
   */
  test("Testing user insertion"){
    val name = "Wojtas"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass)  match {
          case Left(queryError: QueryError) =>
            assert(false, queryError.description)
          case Right(dbUser: User) =>
            assert(dbUser.login == name, s"Returned user's login does not match inserted. Returned: ${dbUser.login}")
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
          case Left(queryError: QueryError) =>
            println(s"${queryError.description}")
            assert(queryError.description == "Sorry Login is taken, try with another one.",
              s"Result error does not contain: ${queryError.description}")
          case Right(dbUser) => assert(false, "This test should return Left(QueryError)")
        }
    }
  }


  /**
   * Testing user insertion when DB is not available,
   * here we switch off DB container before inserting user to them.
   */
  test("Testing user insertion with login present in DB when DB is not available") {
    // here we switch off docker container
    switchOffDbManually()
    //  normally try to execute user insertion.
    val name = "Walo"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass) match {
          case Left(queryError: QueryError) =>
            println(s"${queryError.description}") // prints
            // Server Error: FATAL: terminating connection due to administrator command
            assert(queryError.description == "Connection to DB lost. Try again later.",
              s"""Returned error message
                  |=> \"${queryError.description}
                  |is other than expected
                  |\"Connection to DB lost. Try again later.\"
                """)
          case Right(dbUser) =>
            assert(false, "Returned user login does not match inserted.")
        }
    }
  }



  // chat creation

  /**
   * Create chat when both users information are taken from DB
   */
  test("chat creation when both users are taken from DB") {

    val user1: User = cd.findUser("Walo") match {
      case Left(queryError: QueryError) =>
        //Thread.sleep(120_000)
        assert(false, s"${queryError.description}")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val user2: User = cd.findUser("Spejson") match {
      case Left(queryError: QueryError) =>
        assert(false, s"${queryError.description}")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    val chatId: ChatId     = Domain.generateChatId(user1.userId, user2.userId)
    val chatName: ChatName = "Walo-Spejson"

    cd.createChat(chatId, chatName) match {
      case Right(chat: Chat) =>
        assert(chat.chatId == chatId, s"Chat id from DB: ${chat.chatId} does not match inserted to DB: $chatId")
        assert(chat.chatName == chatName, s"Chat name from DB: ${chat.chatName} does not match inserted to DB: $chatName")
      case Left(queryError: QueryError) =>
        assert(false, queryError.description)
    }
  }





  /**
   * TODO this test may by omitted
   * TODO In this test we try to create chat using users who are absent in db,
   * due to DB constraint in user_chat to use only user_id present in
   * users table db returns exception and test fails.
   */
  test("Testing chat creation using two users which one of them not exists in DB ") {
    // **** not implement yet.
  }

  /**
   * Trying to create chat when DB is unavailable
   */
  test("Trying to create new chat when DB is unavailable") {
    val user1: User = User(UUID.randomUUID(), "pass1")
    val user2: User = User(UUID.randomUUID(), "pass2")

    val chatId: ChatId     = Domain.generateChatId(user1.userId, user2.userId)
    val chatName: ChatName = "ChatName"

    switchOffDbManually() // IMPORTANT we need lost connection to db

    cd.createChat(chatId, chatName) match {
      case Right(chat: Chat) =>
        assert(false, s"""Assertion error, should return
                  |=> \"Connection to DB lost. Try again later.\"
                  |but returned:
                  |=> Chat object: $chat
                  |""")
      case Left(queryError: QueryError) =>
        assert(queryError.description == "Connection to DB lost. Try again later.",
        s"""Wrong error message:
           |=> ${queryError.description}
           |should return:
           |=> \"Connection to DB lost. Try again later.\"""".stripMargin)
    }
  }



  // searching user's chats

  /**
   * Searching user by login when user exists in DB
   */
  test("Searching user by login when user exists in DB") {
    cd.findUser("Spejson") match {
      case Left(queryError: QueryError) =>
        assert(false,
          s"""Assertion error, should find user in db,
             |but returned error:
             |=> ${queryError.description}""".stripMargin)
      case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
    }
  }

  /**
   * Searching user by login when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {
    cd.findUser("NonExistingLogin") match {
      case Left(queryError: QueryError) =>
        assert(queryError.description == "User with this login does not exists.",
          s"""Assertion error, should get error message:
             |=> \"User with this login does not exists.\"
             |but got error:
             |=> ${queryError.description}""".stripMargin)
      case Right(dbUser: User) =>
        assert(false,
          s"""Assertion error, function should return Query error:
             |=> \"User with this login does not exists.\"
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
      case Left(queryError: QueryError) =>
        assert(queryError.description == "Connection to DB lost. Try again later.",
          s"""Assertion error, should get error message:
             |=> \"Connection to DB lost. Try again later.\"
             |but got error:
             |=> ${queryError.description}""".stripMargin)
      case Right(dbUser: User) =>
        assert(false,
          s"""Assertion error, function should return Query error:
             |=> \"User with this login does not exists.\"
             |but returned not existing user:
             |=> ${dbUser.login}""".stripMargin)
    }
  }

  /**
   * Searching user by userId when user exists in DB
   */
  test("Searching user by userID when user exists in DB") {

    // we need take the user id
    val user: User = cd.findUser("Spejson") match {
      case Left(queryError: QueryError) =>
        assert(false, s"${queryError.description}")
        User(UUID.randomUUID(), "")
      case Right(user: User) => user
    }

    cd.findUser(user.userId) match {
      case Left(queryError: QueryError) =>
        assert(false,
          s"""Assertion error, should find user in db,
             |but returned error:
             |=> ${queryError.description}""".stripMargin)
      case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
    }
  }

  /**
   * TODO Searching user by userId when user is unavailable in DB
   */
  test("Searching user by userID when user is unavailable in DB") {

  }

  /**
   * TODO Searching user by userId when DB is down
   */
  test(" Searching user by userID when DB is down.") {

  }








  // searching user's chats

  /**
   * TODO Searching user's chats by user's login when user exists in DB
   */
  test("Searching user by login when user exists in DB") {
//    cd.findUsersChats("Spejson") match {
//      case Failure(exception) =>
//        assert(false, "ERROR BAZY: " + exception.getMessage)
//      case Success(value) =>
//        value match {
//          case Left(value) => assert(false, value.description)
//          case Right(seqChats) => zrobić asercję
//        }
//    }
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



