package com.github.malyszaryczlowiek
package integrationTests.messages.chatExecutorTests

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.QueryErrors
import com.github.malyszaryczlowiek.domain.*
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaTestConfigurator
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.KafkaError
import com.github.malyszaryczlowiek.messages.KessengerAdmin
import com.github.malyszaryczlowiek.account.MyAccount

import integrationTests.messages.KafkaIntegrationTestsTrait
import integrationTests.db.DbIntegrationTestsTrait

import concurrent.ExecutionContext.Implicits.global


class ChatExecutorTests extends KafkaIntegrationTestsTrait, DbIntegrationTestsTrait {

  var user1: User = _
  var user2: User = _
  var cm: ChatManager = _


  override def beforeEach(context: ChatExecutorTests.this.BeforeEach): Unit =
    super.beforeEach(context)
    KessengerAdmin.startAdmin(new KafkaTestConfigurator())

    // we get user object

    ExternalDB.findUser("Walo",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO2L7cYK91Q7Ui9I4HeoAHUf46pq8IdFK",
      "$2a$10$8K1p/a0dL1LXMIgoEDFrwO") match {
      case Left(_)     => throw new Exception("should return user")
      case Right(walo) => user1 = walo
    }


    // we find another user to create chat

    ExternalDB.findUser("Spejson") match {
      case Left(_)        => throw new Exception("should return user")
      case Right(spejson) => user2 = spejson
    }


    // we initilize MyAccount object and assign ChatManager

    cm = MyAccount.initialize(user1) match {
      case Left((dbErrors: Option[QueryErrors], kafkaError: Option[KafkaError])) =>
        println(s"DBError: ${dbErrors},\nKafka Error: $kafkaError")
        throw new Exception("Should return Right object")
      case Right(cm: ChatManager) => cm
    }

    // we create chat with that user

    cm.

    //




  override def afterEach(context: ChatExecutorTests.this.AfterEach): Unit =
    if cm != null then cm.closeChatManager()
    KessengerAdmin.closeAdmin()
    super.afterEach(context)


  test("foo test") {


  }


}
