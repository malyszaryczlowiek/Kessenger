package com.github.malyszaryczlowiek
package integrationTests.messages.chatExecutorTests

import account.MyAccount
import db.ExternalDB
import messages.{MessagePrinter, ChatManager, KessengerAdmin}
import messages.KessengerAdmin
import kessengerlibrary.db.queries.QueryErrors
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.kafka.configurators.KafkaTestConfigurator
import kessengerlibrary.kafka.errors.KafkaError
import integrationTests.messages.KafkaIntegrationTestsTrait
import integrationTests.db.DbIntegrationTestsTrait

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.time.{Duration, LocalDateTime}
import java.util.UUID
import concurrent.ExecutionContext.Implicits.global



class ChatExecutorTests extends KafkaIntegrationTestsTrait, DbIntegrationTestsTrait {

//  var user1: User = _
//  var user2: User = _
//  var chat:  Chat = _
//  var cm: ChatManager = _
//
//
//  override def beforeEach(context: ChatExecutorTests.this.BeforeEach): Unit =
//    super.beforeEach(context)
//    KessengerAdmin.startAdmin(new KafkaTestConfigurator())
//
//    // we get user object
//    ExternalDB.findUser("Walo",
//      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO2L7cYK91Q7Ui9I4HeoAHUf46pq8IdFK",
//      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
//      case Left(_)     => throw new Exception("should return user")
//      case Right(walo) => user1 = walo
//    }
//
//    // we find another user to create chat
//    ExternalDB.findUser("Spejson") match {
//      case Left(_)        => throw new Exception("should return user")
//      case Right(spejson) => user2 = spejson
//    }
//
//    /// create joining topic for user2
//    KessengerAdmin.createJoiningTopic(user2.userId) match {
//      case Left(_)  => throw new Exception( s"should normally create joining topic")
//      case Right(_) => {} // ok
//    }
//
//    // we initilize MyAccount object and assign ChatManager
//    cm = MyAccount.initialize(user1) match {
//      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
//        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
//        throw new Exception("Should return Right object")
//      case Right(cm: ChatManager) => cm
//    }
//
//
//    // create chat
//    ExternalDB.createChat(List(user1, user2), "Chat name") match {
//      case Left(_)            => throw new Exception(s"should return chat object")
//      case Right(chatt: Chat) =>
//        chat = chatt
//        KessengerAdmin.createNewChat(chat) match {
//          case Left(_)      => throw new Exception(s"should return Chat object")
//          case Right(chatt) => println(s"chat $chat created properly via KessengerAdmin. ")
//        }
//    }
//
//
//    // send invitation
//    cm.askToJoinChat(List(user1, user2), chat) match {
//      case Left(_)        => throw new Exception(s"should return chat object")
//      case Right(c: Chat) => chat = c
//    }
//  end beforeEach
//
//
//
//  override def afterEach(context: ChatExecutorTests.this.AfterEach): Unit =
//    if cm != null then cm.closeChatManager()
//    MyAccount.logOut()
//    KessengerAdmin.closeAdmin()
//    super.afterEach(context)
//
//
//
//
//
//
//  /*******************************************
//  TESTS
//   *******************************************/
//
//
//  /**
//   * Testing if user got proper message.
//   */
//  test("Testing if user got proper message.") {
//
//    val chatExecutor: ChatExecutor = MyAccount.getChatExecutor(chat) match {
//      case Some(executor) => executor
//      case None => throw new Exception(s"Should return ChatExecutor object.")
//    }
//
//    val message = "Elo Spejson, gdzie Wojtas?"
//
//    // send message to user2
//    chatExecutor.sendMessage(message)
//
//
//    val user2Consumer = KessengerAdmin.createChatConsumer(user2.userId.toString)
//
//    val topicPartition: TopicPartition = new TopicPartition(chat.chatId, 0)
//
//    user2Consumer.assign(java.util.List.of(topicPartition))
//    // we manually set offset to read from and
//    // we start reading from topic from last read message (offset)
//    user2Consumer.seek(topicPartition, chat.offset)
//
//
//    // checking if user2 get message
//    val records: ConsumerRecords[String, String] = user2Consumer.poll(Duration.ofMillis(250))
//
//    // we assert if exists one record
//    assert(records.count() == 1, s"size is different than 1 != ${records.count()}")
//
//    records.forEach(
//      (r: ConsumerRecord[String, String]) => {
//        val senderUUID = UUID.fromString(r.key())
//        val mess     = r.value()
//        assert(senderUUID == user1.userId, s"sender id not match")
//        assert(mess == message, s"message not match.")
//      }
//    )
//    user2Consumer.close()
//  }
//

}
