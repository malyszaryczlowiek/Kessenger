package com.github.malyszaryczlowiek
package integrationTests.messages.chatManager

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.QueryErrors
import com.github.malyszaryczlowiek.domain.*
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaTestConfigurator
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.KafkaError
import com.github.malyszaryczlowiek.messages.KessengerAdmin
import com.github.malyszaryczlowiek.account.MyAccount

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.immutable.{AbstractMap, SeqMap, SortedMap}
import scala.sys.process.*
import scala.util.{Failure, Success}
import integrationTests.messages.KafkaIntegrationTestsTrait
import integrationTests.db.DbIntegrationTestsTrait

import scala.concurrent.{Await, Future}
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, MILLISECONDS, SECONDS}



/**
 * Note:
 * In these tests we are dependent on Database and KafkaBroker
 *
 * Watch out!!!!
 * Running integration tests with restarting kafka broker before every test
 * are slow.
 *
 */
class ChatManagerTests extends KafkaIntegrationTestsTrait, DbIntegrationTestsTrait {

  var user1: User = _
  var cm: ChatManager = _


  override def beforeEach(context: ChatManagerTests.this.BeforeEach): Unit =
    super.beforeEach(context)
    KessengerAdmin.startAdmin(new KafkaTestConfigurator())
    ExternalDB.findUser("Walo",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO2L7cYK91Q7Ui9I4HeoAHUf46pq8IdFK",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
      case Left(_)     => throw new Exception("should return user")
      case Right(walo) => user1 = walo
    }
    cm = MyAccount.initializeAfterCreation(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }



  override def afterEach(context: ChatManagerTests.this.AfterEach): Unit =
    cm.closeChatManager()
    KessengerAdmin.closeAdmin()
    super.afterEach(context)


  /**
   * This test is empty because all to test is done in beforeEach()
   * and in afterEach()
   */
  test("Initializing user after creation shout return ChatManager correctly.") { }



  test("Sending invitations to existing user via chat manager does not return any error.") {

    val user2: User = ExternalDB.findUser("Spejson",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwOra5VEq4VeXudMZmp9DH9OnhYQ6iDV1e",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
      case Left(_)     => throw new Exception("should return user")
      case Right(user) => user
    }

    val id = Domain.generateChatId(user1.userId, user2.userId)
    val chat = Chat(id, "Chat name", false, 0L, LocalDateTime.now())

    cm.askToJoinChat(List(user2), chat) match {
      case Left(ke: KafkaError) =>
        println(s"$ke")
        assert(false, s"should return Right object.")
      case Right(chat: Chat)    =>
        assert(true)
    }

  }



}