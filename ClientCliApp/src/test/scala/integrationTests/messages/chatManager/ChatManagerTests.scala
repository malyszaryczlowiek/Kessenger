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

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

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



  override def afterEach(context: ChatManagerTests.this.AfterEach): Unit =
    if cm != null then cm.closeChatManager()
    KessengerAdmin.closeAdmin()
    super.afterEach(context)







  /**
   * This test is empty because all to test is done in beforeEach()
   * and in afterEach()
   */
  test("Initializing user after creation shout return ChatManager correctly.") {

    cm = MyAccount.initializeAfterCreation(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

  }



  test("after user initialization, Sending invitations to existing user via chat manager does not return any error.") {

    cm = MyAccount.initializeAfterCreation(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

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


  /**
   * In this test i test if Users data are updated in DB correctly,
   * when joining topic exists but joiningOffset in db is incorrect  (-1L),
   * (not updated, from some reasons in DB)
   *
   */
  test("Updating joining offset from not valid in DB when joiningTopic already exists.") {

    cm = MyAccount.initialize(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }


    // and finally we check if joining offset is updated in DB
    val wal: User = ExternalDB.findUser("Walo",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO2L7cYK91Q7Ui9I4HeoAHUf46pq8IdFK",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
      case Left(_)     => throw new Exception("should return user")
      case Right(walo) => walo
    }

    assert(wal.joiningOffset == 0L, s"joining offset is different: ${wal.joiningOffset}")

  }


  test("Sending invitations to existing user via chat manager does not return any error.") {

    cm = MyAccount.initialize(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

    val user2: User = ExternalDB.findUser("Spejson",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwOra5VEq4VeXudMZmp9DH9OnhYQ6iDV1e",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
      case Left(_)     => throw new Exception("should return user")
      case Right(user) => user
    }

    val id = Domain.generateChatId(user1.userId, user2.userId)
    val anyChat = Chat(id, "Chat name", false, 0L, LocalDateTime.now())

    cm.askToJoinChat(List(user2), anyChat) match {
      case Left(ke: KafkaError) =>
        println(s"$ke")
        assert(false, s"should return Right object.")
      case Right(chat: Chat)    =>
        assert(true)
    }

    // checking weather we do not get invitation from myself.

    val joinConsumer: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
    val topic = new TopicPartition(Domain.generateJoinId(user1.userId), 0)
    joinConsumer.assign(java.util.List.of(topic))
    joinConsumer.seek(topic, 0L)
    val records: ConsumerRecords[String, String] = joinConsumer.poll(java.time.Duration.ofMillis(1000))
    if records.count() != 0 then
      assert(false, "Bad record size in sender topic." )
    joinConsumer.close()

    // checking if user2 got invitation

    val joinConsumer2: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
    val topic2 = new TopicPartition(Domain.generateJoinId(user2.userId), 0)
    joinConsumer2.assign(java.util.List.of(topic2))
    joinConsumer2.seek(topic2, 0L)
    val records2: ConsumerRecords[String, String] = joinConsumer2.poll(java.time.Duration.ofMillis(1000))
    if records2.count() != 1 then
      throw new IllegalStateException("Bad record size.")
    records2.forEach(
      (r: ConsumerRecord[String, String]) => {
        val whoSent = UUID.fromString(r.key())
        val chatId = r.value()
        assert( whoSent.equals(user1.userId), s"Users id not match")
        assert( chatId == id , s"Chat id not match")
      }
    )
    joinConsumer2.close()

  }


}