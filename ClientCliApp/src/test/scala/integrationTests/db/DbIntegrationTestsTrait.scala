package io.github.malyszaryczlowiek
package integrationTests.db

import db.*
import integrationTests.IntegrationTestsTrait

import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID
import scala.util.{Failure, Success}
import sys.process.*


trait DbIntegrationTestsTrait extends munit.FunSuite, IntegrationTestsTrait:


  /**
   * we must give some time to initialize container, because
   * docker container is started as demon and starting script returns immediately
   */
  val waitingTimeMS = 3000
  var switchOffDbEarlier = false
  var fooTime: LocalDateTime = _
  val salt = "$2a$10$8K1p/a0dL1LXMIgoEDFrwO"


  /**
   * Before all integration tests we must set database
   * generating and removing scripts executable. Even if they so.
   */
  override def beforeAll(): Unit =
    super.beforeAll()
    dbBeforeAll()


  def dbBeforeAll(): Unit =
    val executableStartTest = s"chmod +x $pathToScripts/startTestDB".!!
    val executableStopTest = s"chmod +x $pathToScripts/stopTestDB".!!
    fooTime = LocalDateTime.now()

  /**
   * Before Each test we need to start up new DB, Wait for initialization
   * recreate connection to db.
   */
  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
    dbBeforeEach()


  def dbBeforeEach(): Unit =
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
    dbAfterEach()
    super.afterEach(context)


  def dbAfterEach(): Unit =
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



