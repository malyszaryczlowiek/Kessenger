package com.github.malyszaryczlowiek
package messages.kafkaErrorsUtil

enum KafkaErrorType(errorType: String):
  override def toString: String = errorType

  case Warning     extends KafkaErrorType("Warning! ")
  case Error       extends KafkaErrorType("Error! ")
  case FatalError  extends KafkaErrorType("Fatal Error! ")

