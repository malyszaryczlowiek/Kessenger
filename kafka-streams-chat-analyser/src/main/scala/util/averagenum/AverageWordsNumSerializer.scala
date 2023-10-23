package io.github.malyszaryczlowiek
package util.averagenum


import io.circe.syntax._
import org.apache.kafka.common.serialization.Serializer


class AverageWordsNumSerializer extends Serializer[AverageNum] {

  override def serialize(topic: String, data: AverageNum): Array[Byte] =
    data.asJson.noSpaces.getBytes

}