package com.github.malyszaryczlowiek
package integrationTests.db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.db.{ExternalDB, *}
import com.github.malyszaryczlowiek.domain.{PasswordConverter, User}

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

  val pathToScripts = "./src/test/scala/integrationTests/db"
  val waitingTimeMS = 5000

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
    val outputOfDockerStarting = s"./${pathToScripts}/startTestDB".!!
    Thread.sleep(waitingTimeMS) // we must give some time to initialize container
    println(outputOfDockerStarting)
    println("Database prepared...")
    ExternalDB.recreateConnection()
    cd = new ExternalDB()

  /**
   * After Each test we close used connection.
   * @param context
   */
  override def afterEach(context: AfterEach): Unit =
    ExternalDB.closeConnection() match {
      case Failure(exception) => println(exception.getMessage)
      case Success(value) => println("connection closed correctly")
    }
    val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
    println(outputOfDockerStopping)


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
   * TODO Testing user insertion when user with this login exists now in DB
   */
  test("Testing user insertion with login present in DB"){
    val name = "Wojtas"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass) match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == name, "Returned user login does not match inserted.")
        }
    }
  }


  /**
   * TODO Testing user insertion when DB is not available
   * here we switch off DB container.
   */
  test("Testing user insertion with login present in DB") {
    // here we switch off docker container
    val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
    println(outputOfDockerStopping)
    // ando normally try to execute user insertion.
    val name = "Wojtas"
    PasswordConverter.convert("simplePassword") match {
      case Left(value) =>
        assert(false, "UUUUps Password converter failed")
      case Right(pass) =>
        cd.createUser(name, pass) match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == name, "Returned user login does not match inserted.")
        }
    }
  }



  // chat creation

  /**
   * TODO Create chat when both users information are taken from DB
   */
  test("chat creation when both users are taken from DB") {

  }





  /**
   * TODO In this test we try to create chat using users who are absent in db,
   * due to DB constraint in user_chat to use only user_id present in
   * users table db returns exception and test fails.
   */
  test("Testing chat creation using two users which one of them not exists in DB ".fail) {
    // valo exists in DB
    val randomUUID = UUID.randomUUID()
    val walo: User = cd.findUser("Walo") match {
      case Left(queryError: QueryError) =>
        assert(false, s"${queryError.description}")
        User(randomUUID, "foo")
      case Right(user: User) => user
    }
  }

  /**
   * TODO Trying to create chat when DB is unavailable
   */
  test("Trying to create new chat when DB is unavailable") {

  }









  // searching user's chats

  /**
   * TODO Searching user by login when user exists in DB
   */
  test("Searching user by login when user exists in DB") {
    cd.findUser("Spejson") match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
        }
  }

  /**
   * TODO Searching user by login when user is unavailable in DB
   */
  test("Searching user by login when user is unavailable in DB") {

  }

  /**
   * TODO Searching user by login when DB is down
   */
  test(" Searching user by login when DB is down.") {

  }

  /**
   * TODO Searching user by userId when user exists in DB
   */
  test("Searching user by userID when user exists in DB") {

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



