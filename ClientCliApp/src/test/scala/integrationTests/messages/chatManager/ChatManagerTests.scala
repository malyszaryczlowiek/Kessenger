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
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition

import java.util.concurrent.TimeUnit
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
  var chat: Chat = _



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
    MyAccount.logOut()
    KessengerAdmin.closeAdmin()
    super.afterEach(context)






  /*******************************************
  TESTS
  *******************************************/


  /*   Testy do napisania

  1. po utworzeniu usera w db ,MyAccount.initializeAfterCreation
   powinien zwrócić chat managera jeśli Joining topic NIE ISTNIEJEj

  2. po Zalogownaiu usera w db ,MyAccount.initialize
   powinien zwrócić chat managera jeśli Joining topic ISTNIEJE

  3. po Zalogownaiu usera w db ,MyAccount.initialize
   powinien zwrócić chat managera jeśli Joining topic NIE ISTNIEJE






  */



  /**
   * This test is empty because all to test is done in beforeEach()
   * and in afterEach()
   */
  test("Initializing user after creation should return ChatManager correctly.") {

    cm = MyAccount.initializeAfterCreation(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

  }


  /**
   *
   */
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

    //val id = Domain.generateChatId(user1.userId, user2.userId)

    ExternalDB.createChat(List(user1, user2), "Chat name") match {
      case Left(_) => throw new Exception(s"should return chat object")
      case Right(chatt: Chat) => chat = chatt
    }



    cm.askToJoinChat(List(user2), chat) match {
      case Left(ke: KafkaError) =>
        println(s"$ke")
        assert(false, s"should return Right object.")
      case Right(chat: Chat)    =>
        assert(true)
    }

  }


  /**
   * Users data are updated in DB correctly,
   * when joining topic exists but joiningOffset in db is incorrect  (-1L),
   * (not updated, from some reasons in DB)
   *
   */
  test("Updating joining offset from not valid in DB  when joiningTopic already exists.") {

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


  /**
   * Sending invitations to existing user via chat manager
   * does not return any error.
   */
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


    /// create joining topic for user2

    KessengerAdmin.createJoiningTopic(user2.userId) match {
      case Left(_)  => throw new Exception( s"should normally create joining topic")
      case Right(_) => {} // ok
    }


    // define chat
    ExternalDB.createChat(List(user1, user2), "Chat name") match {
      case Left(_) => throw new Exception(s"should return chat object")
      case Right(chatt: Chat) => chat = chatt
    }


    // sending invitation
    cm.askToJoinChat(List(user1, user2), chat) match {
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
    // we should have zero invitations send from myself
    if records.count() != 0 then
      assert(false, s"Bad record size in sender topic: ${records.count()}" )
    joinConsumer.close()


    // checking if user2 got invitation

    val joinConsumer2: KafkaConsumer[String, String] = KessengerAdmin.createJoiningConsumer()
    val topic2 = new TopicPartition(Domain.generateJoinId(user2.userId), 0)
    joinConsumer2.assign(java.util.List.of(topic2))
    joinConsumer2.seek(topic2, 0L)
    val records2: ConsumerRecords[String, String] = joinConsumer2.poll(java.time.Duration.ofMillis(5000))

    // assertion on number of obtained joining invitations
    // user2 should have one invitation to chat.
    assert(  records2.count() == 1 , s"Bad record size: ${records2.count()}")


    records2.forEach(
      (r: ConsumerRecord[String, String]) => {
        val whoSent = UUID.fromString(r.key())
        val chatId = r.value()

        // assertion of information of joining message.
        assert( whoSent.equals(user1.userId), s"Users id not match")
        assert( chatId == chat.chatId , s"Chat id not match")

      }
    )
    joinConsumer2.close()

  }




  /**
   * Sending invitations to existing user via chat manager
   * does not return any error.
   *
   * It is not possible to validate user existence via sending
   * to non existing joining topic.
   *
   */
  test("Sending invitations to NOT existing user via chat manager does not return any error.") {

    cm = MyAccount.initialize(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

    val uuid = UUID.randomUUID()
    println(s"uuid: $uuid")
    val notExistingUser: User = User(uuid, "FooUser" )

    val id = Domain.generateChatId(user1.userId, notExistingUser.userId)
    val anyChat = Chat(id, "Chat name", false, 0L, LocalDateTime.now())

    cm.askToJoinChat(List(notExistingUser), anyChat) match {
      case Left(ke: KafkaError) =>
        println(s"$ke")
        assert(false, s"should return Right object.")
      case Right(chat: Chat)    =>
        assert(chat == anyChat, s"should return $anyChat object.")
    }

  }




  /**
   * Note this SUPER IMPORTANT case...
   *
   * If I do not set broker configuration option
   * auto.create.topics.enable to false,
   * topic is created automatically but with
   * different (default) configurations.
   *
   * After this option is added, test fails,
   * but with different unexpected exception.
   * I expected TopicNotFound but after sending record,
   * when topic does not exists, producer hangs for one minute :O
   *
   */
  test("Sending record to non existing topic, do not throw any Exception") {

    val uuid = UUID.randomUUID()
    println(s"uuid: $uuid")
    val notExistingUser: User = User(uuid, "FooUser" )

    val producer = KessengerAdmin.createJoiningProducer(uuid)

    producer.initTransactions()
    producer.beginTransaction()
    val future = producer.send(new ProducerRecord[String, String]("NON_EXISTING_TOPIC", "KEY", "VALUE") )

    producer.commitTransaction()

    producer.close()
  }


}