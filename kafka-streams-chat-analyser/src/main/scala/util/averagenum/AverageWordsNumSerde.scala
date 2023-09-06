package io.github.malyszaryczlowiek
package util.averagenum

import org.apache.kafka.common.serialization.{Deserializer, Serde, Serializer}

class AverageWordsNumSerde extends Serde[AverageNum] {

  override def serializer(): Serializer[AverageNum] =
    new AverageWordsNumSerializer

  override def deserializer(): Deserializer[AverageNum] =
    new AverageWordsNumDeserializer
}