package com.github.malyszaryczlowiek
package integrationTests.messages.kessengerAdmin

import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.Domain
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaTestConfigurator
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage}

import java.time.LocalDateTime
import scala.sys.process.*
import scala.util.{Failure, Success}
import java.util.UUID


class KessengerAdminTests extends munit.FunSuite :


  val pathToScripts = "./src/test/scala/integrationTests/scripts"
  //val waitingTimeMS = 5000
  private var user: User = _
  private var isKafkaBrokerRunning = true




  override def beforeAll(): Unit =
    super.beforeAll()

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

    user = User(UUID.randomUUID(), "Login")



  /**
   * Before each test we start fresh kafka broker
   * @param context
   */
  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
    isKafkaBrokerRunning = true
    val outputOfZookeeperStarting = s"./${pathToScripts}/startZookeeper".!!
    Thread.sleep(50) // wait for zookeeper initialization
    val outputOfKafkaStarting = s"./${pathToScripts}/startKafka".!!
    println(s"Created kafka-test-container container")
    Thread.sleep(50)
    KessengerAdmin.startAdmin(new KafkaTestConfigurator)



  /**
   * Closing kafka broker after each test.
   * @param context
   */
  override def afterEach(context: AfterEach): Unit =
    KessengerAdmin.closeAdmin()
    if isKafkaBrokerRunning then
      switchOffKafkaBroker()
//    val outputOfKafkaStopping = s"./${pathToScripts}/stopKafka".!!
//    val name = outputOfKafkaStopping.split('\n')
//    println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
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


  private def switchOffKafkaBroker(): Unit =
    val outputOfKafkaStopping = s"./${pathToScripts}/stopKafka".!!
    val name = outputOfKafkaStopping.split('\n')
    println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
    isKafkaBrokerRunning = false





  /*
  Testing creation of joining topicu
  */

  test("Creating non existing joining topic should return Right(Chat) object") {

    val userId: UserID = UUID.randomUUID()

    KessengerAdmin.createJoiningTopic( userId ) match {
      case Left(kafkaError: KafkaError) =>
        assert(false,
          s"Should return Right object but returned: ${kafkaError.description}")
      case Right(_) =>
    }
  }


  test("Create joining topic when this topic already exists should return KafkaErrorMessage.ServerError") {

    val userId: UserID = UUID.randomUUID()

    KessengerAdmin.createJoiningTopic( userId ) match {
      case Left(kafkaError: KafkaError) =>
        assert(false, s"Should return Right object but returned: ${kafkaError.description}")
      case Right(_) =>   // is ok, should return Right()
    }

    /*
    when trying create existineg topic we should gain ServerError
    */
    KessengerAdmin.createJoiningTopic( userId ) match {
      case Left(kafkaError: KafkaError) =>
        assert(kafkaError.description == KafkaErrorMessage.ServerError,
          s"Should return ServerError object but returned: ${kafkaError.description}")
      case Right(_) =>
        assert(false, s"Should return Left(KafkaError) object but returned Right")
    }
  }


  /**
   * This test may take above 15 seconds because of
   * KessengerAdmin.createJoiningTopic() ~5s when cannot connect to kafka broker, and
   * KessengerAdmin.close() takes ~5s
   *
   *
   */
  test("Creating new joining topic when kafka broker is down should return KafkaErrorMessage.ServerError") {
    val userId: UserID = UUID.randomUUID()

    // switch off Kafka Broker
    switchOffKafkaBroker()


    KessengerAdmin.createJoiningTopic( userId ) match {
      case Left(kafkaError: KafkaError) =>
        assert(kafkaError.description == KafkaErrorMessage.ServerError,
          s"Should return ServerError object but returned: ${kafkaError.description}")
      case Right(_) =>
        assert(false, s"Should return Left(KafkaError) object but returned Right")
    }
  }




  /*
  Testing chat topic creation
  */

  test("Creating chat topic should return ") {

    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())

    KessengerAdmin.createNewChat(chat) match {
      case Left(KafkaError(_, description: KafkaErrorMessage))
        => assert(false, s"should return right with chat: $chat")
      case Right(returnedChat)
        => assert(returnedChat == chat, s"chats do not match.")
    }
  }



  test("If chat already exists, should return KafkaErrorMessage.ServerError"){

    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())

    KessengerAdmin.createNewChat(chat) match {
      case Left(KafkaError(_, description: KafkaErrorMessage))
        => throw new IllegalStateException("Should return Right")
      case Right(returnedChat)
        => assert(returnedChat == chat, s"chats do not match.")
    }

    KessengerAdmin.createNewChat(chat) match {
      case Left(KafkaError(_, description: KafkaErrorMessage))
        => assert(description == KafkaErrorMessage.ServerError, s"should return KafkaErrorMessage.ServerError.")
      case Right(returnedChat)
        => assert(false, s"Should return KafkaErrorMessage.ServerError.")
    }

  }


  test("If kafka broker is down, should return KafkaErrorMessage.ServerError"){

    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())

    // turn off kafka broker
    switchOffKafkaBroker()


    KessengerAdmin.createNewChat(chat) match {
      case Left(KafkaError(_, description: KafkaErrorMessage))
      => assert(description == KafkaErrorMessage.ServerError, s"should return KafkaErrorMessage.ServerError.")
      case Right(returnedChat)
      => assert(false, s"Should return KafkaErrorMessage.ServerError.")
    }

  }

















