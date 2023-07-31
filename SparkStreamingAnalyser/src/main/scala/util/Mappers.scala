package io.github.malyszaryczlowiek
package util

import kessengerlibrary.model.{Message, SparkMessage}
import kessengerlibrary.model.postanalysis.{WindowedAvgServerDelay, WindowedAvgServerDelayByUser, WindowedAvgServerDelayByZone}
import kessengerlibrary.serdes.message.MessageDeserializer
import kessengerlibrary.serdes.postanalysis.WindowedAvgServerDelayByZoneSerializer
import kessengerlibrary.serdes.postanalysis.windowed.avgserverdelay.WindowedAvgServerDelaySerializer
import kessengerlibrary.serdes.postanalysis.windowed.avgserverdelaybyuser.WindowedAvgServerDelayByUserSerializer

import org.apache.spark.sql.Row

import java.math.MathContext
import java.sql.Timestamp
import java.time.{Instant, ZoneId}
import java.util.UUID
import scala.math.BigDecimal.RoundingMode

object Mappers {


  /**
   * with this mapper we convert and deserialize data from Kafka Broker
   * to data for Spark
   * @return
   */
  def fromKafkaMapper: Row => SparkMessage = (r: Row) => {
    val mByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
    val serverTime: Timestamp   = r.getAs[Timestamp]("timestamp") // .getTime
    // deserializer must be created inside mapper, otherwise initialization causes exceptions
    val messageDeserializer     = new MessageDeserializer
    // we deserialize our message from Array[Byte] to Message object
    val message: Message        = messageDeserializer.deserialize("", mByteArray)
    val messageTime: Timestamp  = Timestamp.from(Instant.ofEpochMilli(message.sendingTime))

    // logger.trace(s"$serverTime ${message.authorLogin} >> ${message.content}")

    SparkMessage(
      serverTime, //  time of receiving message by kafka // Long
      message.chatId, // string
      message.chatName, // string
      message.groupChat, // boolean
      message.zoneId.toString,
      messageTime, //        message.utcTime, // sending time by user (do not mistake with timestamp which is time when kafka broker gets message)
      message.content, // string
      message.authorId.toString, // UUID of author converted to string
      message.authorLogin // String
    )
  }



  def avgDelayToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val w = WindowedAvgServerDelay(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      r.getAs[Long]("avg_diff_time_ms")
    )
    val serializer = new WindowedAvgServerDelaySerializer
    val serialized = serializer.serialize("", w)
    KafkaOutput(null, serialized)
  }



  def avgDelayByUserToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val avg = BigDecimal.decimal( r.getAs[Double]("avg_diff_time_ms") ).setScale(0, RoundingMode.HALF_UP).toLong
    val w = WindowedAvgServerDelayByUser(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      UUID.fromString( r.getAs[String]("user_id") ),
      avg
    )
    val serializer = new WindowedAvgServerDelayByUserSerializer
    val serialized = serializer.serialize("", w)
    KafkaOutput(null, serialized)
  }



  def avgDelayByZoneToKafkaMapper: Row => KafkaOutput = (r: Row) => {
    val w = WindowedAvgServerDelayByZone(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      ZoneId.of(r.getAs[String]("zone_id")),
      r.getAs[Long]("avg_diff_time_ms")
    )
    val serializer = new WindowedAvgServerDelayByZoneSerializer
    val serialized = serializer.serialize("", w)
    KafkaOutput(null, serialized)
  }

}
