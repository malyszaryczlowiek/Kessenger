package com.github.malyszaryczlowiek
package integrationTests.messages

import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, KafkaErrors, KessengerAdmin}
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaTestConfigurator

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.immutable.{AbstractMap, SeqMap, SortedMap}
import scala.util.{Failure, Success}
import sys.process.*


/**
 * Watch out!!!!
 * Running integration tests with restarting kafka broker before every test
 * are extremely slow.
 *
 */
class ChatManagerTests extends munit.FunSuite {

  val pathToScripts = "./src/test/scala/integrationTests/messages"
  val waitingTimeMS = 5000

  override def beforeAll(): Unit =
    val makeZookeeperStartScriptExecutable = s"chmod +x ${pathToScripts}/startZookeeper".!!
    println( makeZookeeperStartScriptExecutable )

    val makeZookeeperStopScriptExecutable = s"chmod +x ${pathToScripts}/stopZookeeper".!!
    println( makeZookeeperStopScriptExecutable )

    val makeKafkaStartScriptExecutable = s"chmod +x ${pathToScripts}/startKafka".!!
    println( makeKafkaStartScriptExecutable )

    val makeKafkaStopScriptExecutable = s"chmod +x ${pathToScripts}/stopKafka".!!
    println( makeKafkaStopScriptExecutable )

    val makeCreateTopicScriptExecutable = s"chmod +x ${pathToScripts}/createTopic".!!
    println( makeCreateTopicScriptExecutable )

    super.beforeAll()

  /**
   * Before each test we start fresh kafka broker
   * @param context
   */
  override def beforeEach(context: BeforeEach): Unit =
    val outputOfZookeeperStarting = s"./${pathToScripts}/startZookeeper".!!
    Thread.sleep(50) // wait for zookeeper initialization
    val outputOfKafkaStarting = s"./${pathToScripts}/startKafka".!!
    println(s"Created kafka-test-container container")
    Thread.sleep(50)
    KessengerAdmin.startAdmin(new KafkaTestConfigurator)
    super.beforeEach(context)


  /**
   * Closing kafka broker after each test.
   * @param context
   */
  override def afterEach(context: AfterEach): Unit =
    KessengerAdmin.closeAdmin()
    val outputOfKafkaStopping = s"./${pathToScripts}/stopKafka".!!
    val name = outputOfKafkaStopping.split('\n')
    println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
    Thread.sleep(50)
    val outputOfZookeeperStopping = s"./${pathToScripts}/stopZookeeper".!!
    val names = outputOfZookeeperStopping.split('\n')
    println( s"Stopped ${names(0)} container\nDeleted ${names(1)} container\nDeleted \'${names(2)}\' docker testing network" )
    super.afterEach(context)

  /**
   * closing zookeeper and removing local docker testing network
   * after all tests.
   */
  override def afterAll(): Unit = super.afterAll()

  private def createTopic(topicName: String): Unit =
    val createTopic = s"./${pathToScripts}/createTopic $topicName".!!
    println( createTopic )


  test("create topic") {
    val fakeChat = Chat("fake-chat-id", "fake-chat-name", false, 0L, LocalDateTime.now())
    KessengerAdmin.createNewChat(fakeChat) match {
      case Left(kafkaErrors: KafkaErrors)  => assert(false,s"should not return any kafkaError")
      case Right(value)                    => assert(value == fakeChat.chatId, s"$value")
    }
  }


  // org.apache.kafka.common.errors.TopicExistsException: Topic 'fake-chat-id' already exists.

  test("create topic and remove them") {
    val fakeChat = Chat("fake-chat-id", "fake-chat-name", false, 0L, LocalDateTime.now())
    KessengerAdmin.createNewChat(fakeChat) match {
    case Left(kafkaErrors: KafkaErrors)  => assert(false, s"should not throw any exception")
    case Right(value)                    => assert(value == fakeChat.chatId, s"$value")
    }

    KessengerAdmin.removeChat(fakeChat) match {
      case Left(value) => assert(false, "should not return kafka error")
      case Right(value) => assert(value == fakeChat.chatId, "Not matching returned chat id")
    }
  }

  test("create topic and send message") {
    val fakeChat = Chat("fake-chat-id", "fake-chat-name", false, 0, LocalDateTime.now())

    KessengerAdmin.createNewChat(fakeChat) match {
      case Left(kafkaErrors: KafkaErrors)  => assert(false, s"should not throw any exception")
      case Right(value)                    => assert(value == fakeChat.chatId, s"$value")
    }

    val sender = User(UUID.randomUUID(), "sender")
    val reader = User(UUID.randomUUID(), "reader")

    val ce = new ChatExecutor(sender, fakeChat, List(sender, reader)) // this no prints
    val ce2 = new ChatExecutor(reader, fakeChat, List(sender, reader)) // this prints sent message

    ce.sendMessage("Rikitiki narkotyki :D")
    // ce.sendMessage("Second message. ")

    Thread.sleep(1000)
    ce.closeChat()
    ce2.closeChat()

    KessengerAdmin.removeChat(fakeChat) match {
      case Left(_)      => assert(false, "should not return kafka error")
      case Right(value) => assert(value == fakeChat.chatId, "Not matching returned chat id")
    }
  }
}



//
//
//  test("Testing topic creation script") {
//    createTopic("foo")
//    assert(1 == 1, "failed one equality")
//  }
//
//  test("Testing with another topic") {
//    createTopic("bar")
//    assert(1 == 1, "failed one equality")
//  }