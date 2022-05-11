package com.github.malyszaryczlowiek
package messages.kafkaErrorsUtil

enum KafkaErrorMessage(message: String) :
  override def toString: String = message

  case TimeoutError   extends KafkaErrorMessage("Timeout Server Error. ")
  case UndefinedError extends KafkaErrorMessage("Undefined Error. ")
  case InternalError  extends KafkaErrorMessage("Internal Error. ")
  case ServerError    extends KafkaErrorMessage("Server Error. ")
