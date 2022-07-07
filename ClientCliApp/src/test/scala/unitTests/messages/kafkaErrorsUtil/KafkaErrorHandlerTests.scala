package com.github.malyszaryczlowiek
package unitTests.messages.kafkaErrorsUtil

import kessengerlibrary.kafka.errors.*
import org.apache.kafka.common.errors.*



class KafkaErrorHandlerTests extends munit.FunSuite:

  test("KafkaErrorsHandler.handleThrowable() (deprecated) method correctly handle General Throwable error type") {

    val error: Throwable = new TopicExistsException("Topic name")

    KafkaErrorsHandler.handleThrowable[Unit](error) match {
      case Left(kafkaError: KafkaError) =>
        println(s"${kafkaError.description}")
        assert(kafkaError.description == KafkaErrorMessage.ServerError, s"Should return ServerError object but returned: ${kafkaError.description}")
      case _ => assert(false)
    }

    val error2: Throwable = new IllegalArgumentException("Topic name")

    KafkaErrorsHandler.handleThrowable[Unit](error2) match {
      case Left(kafkaError: KafkaError) =>
        println(s"${kafkaError.description}")
        assert(kafkaError.description == KafkaErrorMessage.InternalError, s"Should return InternalError object but returned: ${kafkaError.description}")
      case _ => assert(false)
    }

    val error3: Throwable = new Throwable("Topic name")

    KafkaErrorsHandler.handleThrowable[Unit](error3) match {
      case Left(kafkaError: KafkaError) =>
        println(s"${kafkaError.description}")
        assert(kafkaError.description == KafkaErrorMessage.UndefinedError, s"Should return UndefinedError object but returned: ${kafkaError.description}")
      case _ => assert(false)
    }

  }
