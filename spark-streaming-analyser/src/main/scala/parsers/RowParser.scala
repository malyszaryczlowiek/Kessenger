package io.github.malyszaryczlowiek
package parsers

import kessengerlibrary.model.postanalysis.{WindowedAvgServerDelay, WindowedAvgServerDelayByUser, WindowedAvgServerDelayByZone}
import kessengerlibrary.model.{Message, SparkMessage}
import kessengerlibrary.serdes.message.MessageDeserializer

import java.sql.Timestamp
import java.time.{Instant, ZoneId}
import java.util.UUID
import scala.math.BigDecimal.RoundingMode
import org.apache.spark.sql.Row
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


class  RowParser
object RowParser {

  private val logger: Logger = LogManager.getLogger(classOf[RowParser])

  def kafkaInputRowParser:  Row => SparkMessage = (r: Row) => {
    logger.warn(s"processing input Row")

    val mByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
    val serverTime: Timestamp   = r.getAs[Timestamp]("timestamp") // .getTime
    // deserializer must be created inside mapper, otherwise initialization causes exceptions
    val messageDeserializer     = new MessageDeserializer
    // we deserialize our message from Array[Byte] to Message object
    val message: Message        = messageDeserializer.deserialize("", mByteArray)
    val messageTime: Timestamp  = Timestamp.from(Instant.ofEpochMilli(message.sendingTime))

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


  def avgServerDelayParser: Row => WindowedAvgServerDelay = (r: Row) => {
    WindowedAvgServerDelay(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      r.getAs[Long]("avg_diff_time_ms")
    )
  }


  def avgServerDelayByUserParser: Row => WindowedAvgServerDelayByUser = (r: Row) => {
    val avg = BigDecimal.decimal( r.getAs[Double]("avg_diff_time_ms") ).setScale(0, RoundingMode.HALF_UP).toLong
    WindowedAvgServerDelayByUser(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      UUID.fromString(r.getAs[String]("user_id")),
      avg
    )
  }


  def avgServerDelayByZoneParser: Row => WindowedAvgServerDelayByZone = (r: Row) => {
    WindowedAvgServerDelayByZone(
      r.getAs[Timestamp]("window_start"),
      r.getAs[Timestamp]("window_end"),
      ZoneId.of(r.getAs[String]("zone_id")),
      r.getAs[Long]("avg_diff_time_ms")
    )
  }






}
