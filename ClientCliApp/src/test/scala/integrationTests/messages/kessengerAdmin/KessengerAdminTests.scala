package com.github.malyszaryczlowiek
package integrationTests.messages.kessengerAdmin


import messages.{ChatExecutor, ChatManager, KessengerAdmin}
import integrationTests.messages.KafkaIntegrationTestsTrait

import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.domain.Domain
import kessengerlibrary.domain.Domain.UserID
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorMessage}

import java.time.LocalDateTime
import java.util.UUID
import scala.sys.process.*
import scala.util.{Failure, Success}



class KessengerAdminTests extends KafkaIntegrationTestsTrait {

//
//  /*
//  Testing creation of joining topicu
//  */
//
//  test("Creating non existing joining topic should return Right(Chat) object") {
//
//    val userId: UserID = UUID.randomUUID()
//
//    KessengerAdmin.createJoiningTopic( userId ) match {
//      case Left(kafkaError: KafkaError) =>
//        assert(false,
//          s"Should return Right object but returned: ${kafkaError.description}")
//      case Right(_) =>
//    }
//  }
//
//
//  test("Create joining topic when this topic already exists should return KafkaErrorMessage.ServerError") {
//
//    val userId: UserID = UUID.randomUUID()
//
//    KessengerAdmin.createJoiningTopic( userId ) match {
//      case Left(kafkaError: KafkaError) =>
//        assert(false, s"Should return Right object but returned: ${kafkaError.description}")
//      case Right(_) =>   // is ok, should return Right()
//    }
//
//    /*
//    when trying create existineg topic we should gain ServerError
//    */
//    KessengerAdmin.createJoiningTopic( userId ) match {
//      case Left(kafkaError: KafkaError) =>
//        assert(kafkaError.description == KafkaErrorMessage.ServerError,
//          s"Should return ServerError object but returned: ${kafkaError.description}")
//      case Right(_) =>
//        assert(false, s"Should return Left(KafkaError) object but returned Right")
//    }
//  }
//
//
//  /**
//   * This test may take above 15 seconds because of
//   * KessengerAdmin.createJoiningTopic() ~5s when cannot connect to kafka broker, and
//   * KessengerAdmin.close() takes ~5s
//   *
//   *
//   */
//  test("Creating new joining topic when kafka broker is down should return KafkaErrorMessage.ServerError") {
//    val userId: UserID = UUID.randomUUID()
//
//    // switch off Kafka Broker
//    switchOffKafkaBroker()
//
//
//    KessengerAdmin.createJoiningTopic( userId ) match {
//      case Left(kafkaError: KafkaError) =>
//        assert(kafkaError.description == KafkaErrorMessage.ServerError,
//          s"Should return ServerError object but returned: ${kafkaError.description}")
//      case Right(_) =>
//        assert(false, s"Should return Left(KafkaError) object but returned Right")
//    }
//  }
//
//
//
//
//  /*
//  Testing chat topic creation
//  */
//
//  test("Creating chat topic should return ") {
//
//    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
//    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())
//
//    KessengerAdmin.createNewChat(chat) match {
//      case Left(KafkaError(_, description: KafkaErrorMessage))
//        => assert(false, s"should return right with chat: $chat")
//      case Right(returnedChat)
//        => assert(returnedChat == chat, s"chats do not match.")
//    }
//  }
//
//
//
//  test("If chat already exists, should return KafkaErrorMessage.ServerError"){
//
//    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
//    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())
//
//    KessengerAdmin.createNewChat(chat) match {
//      case Left(KafkaError(_, description: KafkaErrorMessage))
//        => throw new IllegalStateException("Should return Right")
//      case Right(returnedChat)
//        => assert(returnedChat == chat, s"chats do not match.")
//    }
//
//    KessengerAdmin.createNewChat(chat) match {
//      case Left(KafkaError(_, description: KafkaErrorMessage))
//        => assert(description == KafkaErrorMessage.ServerError, s"should return KafkaErrorMessage.ServerError.")
//      case Right(returnedChat)
//        => assert(false, s"Should return KafkaErrorMessage.ServerError.")
//    }
//
//  }
//
//
//  test("If kafka broker is down, should return KafkaErrorMessage.ServerError"){
//
//    val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
//    val chat : Chat = Chat(chatId, "Chat name", false, 0L, LocalDateTime.now())
//
//    // turn off kafka broker
//    switchOffKafkaBroker()
//
//
//    KessengerAdmin.createNewChat(chat) match {
//      case Left(KafkaError(_, description: KafkaErrorMessage))
//      => assert(description == KafkaErrorMessage.ServerError, s"should return KafkaErrorMessage.ServerError.")
//      case Right(returnedChat)
//      => assert(false, s"Should return KafkaErrorMessage.ServerError.")
//    }
//
//  }

}





