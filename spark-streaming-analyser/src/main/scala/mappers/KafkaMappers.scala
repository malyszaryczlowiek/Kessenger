package io.github.malyszaryczlowiek
package mappers

import kessengerlibrary.serdes.postanalysis.WindowedAvgServerDelayByZoneSerializer
import kessengerlibrary.serdes.postanalysis.windowed.avgserverdelay.WindowedAvgServerDelaySerializer
import kessengerlibrary.serdes.postanalysis.windowed.avgserverdelaybyuser.WindowedAvgServerDelayByUserSerializer
import output.KafkaOutput
import parsers.RowParser._
import org.apache.spark.sql.Row

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class KafkaMappers
object KafkaMappers {

  private val logger: Logger = LogManager.getLogger(classOf[KafkaMappers])

  def avgDelayToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val w = avgServerDelayParser(r)
    val serializer = new WindowedAvgServerDelaySerializer
    val serialized = serializer.serialize("", w)
    KafkaOutput(null, serialized)
  }



  def avgDelayByUserToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val w = avgServerDelayByUserParser(r)
    val serializer = new WindowedAvgServerDelayByUserSerializer
    val serialized = serializer.serialize("", w)
    logger.warn(s"avgDelayByUserToKafkaMapper data serialized and KafkaOutput object should be send to kafka.  ")
    KafkaOutput(null, serialized)
  }



  def avgDelayByZoneToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val w = avgServerDelayByZoneParser(r)
    val serializer = new WindowedAvgServerDelayByZoneSerializer
    val serialized = serializer.serialize("", w)
    KafkaOutput(null, serialized)
  }

}
