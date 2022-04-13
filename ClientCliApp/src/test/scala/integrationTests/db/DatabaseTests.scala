package com.github.malyszaryczlowiek
package integrationTests.db

import com.github.malyszaryczlowiek.db.{ExternalDB, *}

import java.sql.Connection
import scala.util.{Failure, Success}
import sys.process.*


/**
 * Integration tests for DB.
 * Each test runs on separate freshly build DB container
 */
class DatabaseTests extends munit.FunSuite:

  private var cd: ExternalDB = _

  val pathToScripts = "./src/test/scala/integrationTests/db"
  val waitingTimeMS = 5000

  /**
   * Before all integration test we must set database
   * generating and removing scripts executable. Even if they so.
   */
  override def beforeAll(): Unit =
    val executableStartTest = s"chmod +x ${pathToScripts}/startTestDB".!!
    val executableStopTest = s"chmod +x ${pathToScripts}/stopTestDB".!!
    super.beforeAll()

  /**
   * Each test has its own connection,
   * @param context
   */
  override def beforeEach(context: BeforeEach): Unit =
    val outputOfDockerStarting = s"./${pathToScripts}/startTestDB".!!
    Thread.sleep(waitingTimeMS) // we must give some time to initialize
    println(outputOfDockerStarting)
    println("Database prepared...")
    ExternalDB.recreateConnection()
    cd = new ExternalDB()

  override def afterEach(context: AfterEach): Unit =
    ExternalDB.closeConnection() match {
      case Failure(exception) => println(exception.getMessage)
      case Success(value) => println("connection closed correctly")
    }
    val outputOfDockerStopping = s"./${pathToScripts}/stopTestDB".!!
    println(outputOfDockerStopping)


//  override def afterAll(): Unit =
//    ExternalDB.closeConnection()
//    super.afterAll()




  test("Testing user insertion"){
    cd = new ExternalDB()
    cd.createUser("name", "pass") match {
      case Failure(exception) =>
        println(exception.getMessage)
        assert(false, "ERROR BAZY: " + exception.getMessage)
      case Success(value) =>
        value match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == "name", "There is no such user")
        }
    }
  }

  test("Testing user searching by login"){
    cd = new ExternalDB()
    cd.findUser("Spejson" ) match {
      case Failure(exception) =>
        assert(false, "ERROR BAZY: " + exception.getMessage)
      case Success(value) =>
        value match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == "Spejson", "Not the same login")
        }
    }
  }
