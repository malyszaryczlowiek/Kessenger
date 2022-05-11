package com.github.malyszaryczlowiek
package unitTests.messages

import com.github.malyszaryczlowiek.db.*
import com.github.malyszaryczlowiek.messages.KessengerAdmin
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaTestConfigurator
import com.github.malyszaryczlowiek.domain.User

import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, MILLISECONDS, SECONDS}
import scala.util.{Failure, Success}
import sys.process.*
import concurrent.ExecutionContext.Implicits.global

class ChatManagerTests extends munit.FunSuite :

  val pathToKafkaScripts = "./src/test/scala/integrationTests/messages"
  val pathToDatabaseScripts = "./src/test/scala/integrationTests/db"
  val waitingTimeMS = 5000
  private var user: User = _

  override def beforeAll(): Unit =
    super.beforeAll()
    val makeZookeeperStartScriptExecutable = s"chmod +x $pathToKafkaScripts/startZookeeper".!!
    val makeZookeeperStopScriptExecutable  = s"chmod +x $pathToKafkaScripts/stopZookeeper".!!
    val makeKafkaStartScriptExecutable     = s"chmod +x $pathToKafkaScripts/startKafka".!!
    val makeKafkaStopScriptExecutable      = s"chmod +x $pathToKafkaScripts/stopKafka".!!
    val makeCreateTopicScriptExecutable    = s"chmod +x $pathToKafkaScripts/createTopic".!!
    val executableStartTest                = s"chmod +x $pathToDatabaseScripts/startTestDB".!!
    val executableStopTest                 = s"chmod +x $pathToDatabaseScripts/stopTestDB".!!



  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)

    val kafkaFuture: Future[Unit] = Future {
      val outputOfZookeeperStarting = s"./$pathToKafkaScripts/startZookeeper".!!
      Thread.sleep(50) // wait for zookeeper initialization
      val outputOfKafkaStarting = s"./$pathToKafkaScripts/startKafka".!!
      println(s"Created kafka-test-container container")
      KessengerAdmin.startAdmin(new KafkaTestConfigurator)
    }

    val databaseFuture: Future[Unit] = Future {
      val outputOfDockerStarting = s"./$pathToDatabaseScripts/startTestDB".!!
      Thread.sleep(waitingTimeMS)
      println(outputOfDockerStarting)
      println("Database prepared...")
      Thread.sleep(50)
      ExternalDB.recreateConnection()
    }

    val waitForAll = kafkaFuture.zip(databaseFuture)
    Await.result(waitForAll, Duration.create(5L, SECONDS))







  override def afterEach(context: AfterEach): Unit =

    val kafkaClosing = Future {
      KessengerAdmin.closeAdmin()
      val outputOfKafkaStopping = s"./$pathToKafkaScripts/stopKafka".!!
      val name = outputOfKafkaStopping.split('\n')
      println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
      Thread.sleep(50)
      val outputOfZookeeperStopping = s"./$pathToKafkaScripts/stopZookeeper".!!
      val names = outputOfZookeeperStopping.split('\n')
      println( s"Stopped ${names(0)} container\nDeleted ${names(1)} container\nDeleted \'${names(2)}\' docker testing network" )
    }

    val databaseClosing = Future {
      ExternalDB.closeConnection() match {
        case Failure(ex) => println(ex.getMessage)
        case Success(value) => println("connection closed correctly")
      }
    }

    val waitForAll = kafkaClosing.zip(databaseClosing)
    Await.result(waitForAll, Duration.create(5L, SECONDS))
    super.afterEach(context)

  /**
   * closing zookeeper and removing local docker testing network
   * after all tests.
   */
  override def afterAll(): Unit = super.afterAll()



  test("") {
    assert(true)
  }

