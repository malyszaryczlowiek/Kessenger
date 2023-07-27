package io.github.malyszaryczlowiek
package util

import kessengerlibrary.model.{Message, SparkMessage}
import kessengerlibrary.serdes.message.MessageDeserializer

import org.apache.spark.sql.Row

import java.sql.Timestamp
import java.time.{Instant, ZoneId}

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



  def avgDelayToKafkaMapper: Row => KafkaOutput = {

  }

  case class WindowedAvgServerDelayByZone(windowStart: Timestamp, windowEnd: Timestamp, zoneId: ZoneId, delayMS: Long)

  def avgDelayByZoneToKafkaMapper: Row => KafkaOutput = (r: Row) => {

    WindowedAvgServerDelayByZone(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      ZoneId.of( r.getAs[String]("zone_id") ),
      r.getAs[Long]("avg_diff_time_ms")
    )


    val serializer = new KafkaOutputSerializer
    serializer.serialize()

  }




  def avgDelayByUserToKafkaMapper: Row => KafkaOutput = {

  }

}
