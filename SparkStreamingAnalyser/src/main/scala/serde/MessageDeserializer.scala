package com.github.malyszaryczlowiek
package serde

import org.apache.kafka.common.serialization.Deserializer
import io.circe.parser.decode


class MessageDeserializer extends Deserializer[Message] {

  override def deserialize(topic: String, data: Array[Byte]): Message = {
    import Message.decoder
    decode[Message](new String(data)) match {
      case Left(_)            => Message.nullMessage
      case Right(m: Message)  => m
    }
  }

}