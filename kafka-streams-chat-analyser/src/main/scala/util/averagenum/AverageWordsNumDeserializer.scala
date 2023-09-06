package io.github.malyszaryczlowiek
package util.averagenum

import io.circe.parser.decode
import org.apache.kafka.common.serialization.Deserializer


class AverageWordsNumDeserializer extends Deserializer[AverageNum] {

  override def deserialize(topic: String, data: Array[Byte]): AverageNum =
    decode[AverageNum](new String(data)) match {
      case Left(_) => null
      case Right(m: AverageNum) => m
    }

}
