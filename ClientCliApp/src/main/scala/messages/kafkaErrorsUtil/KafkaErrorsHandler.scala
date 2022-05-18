package com.github.malyszaryczlowiek
package messages.kafkaErrorsUtil

import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.*

object KafkaErrorsHandler :

  def handle[A](ex: Throwable): Either[KafkaError, A] =
    val internalError   = Left(KafkaError(KafkaErrorType.FatalError, KafkaErrorMessage.InternalError))
    val chatExistsError = Left(KafkaError(KafkaErrorType.Warning, KafkaErrorMessage.ChatExistsError))
    val serverError     = Left(KafkaError(KafkaErrorType.FatalError, KafkaErrorMessage.ServerError))
    val undefinedErr    = Left(KafkaError(KafkaErrorType.FatalError, KafkaErrorMessage.UndefinedError))
    try { throw ex }
    catch {
      case e: InvalidOffsetException       => internalError
      case e: WakeupException              => internalError
      case e: InterruptException           => internalError
      case e: AuthenticationException      => internalError
      case e: AuthorizationException       => internalError
      case e: IllegalArgumentException     => internalError
      case e: IllegalStateException        => internalError
      case e: ArithmeticException          => internalError
      case e: InvalidTopicException        => internalError
      case e: TopicExistsException         => chatExistsError
      case e: UnsupportedVersionException  => serverError
      case e: KafkaException               => undefinedErr
      case e: Throwable                    => undefinedErr
    }


/*

*/