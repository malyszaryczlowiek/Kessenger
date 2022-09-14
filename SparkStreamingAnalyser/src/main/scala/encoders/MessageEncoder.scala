package io.github.malyszaryczlowiek
package encoders

import kessengerlibrary.domain.Domain.{ServerTime, ChatId, ChatName, Content, StrUserID, Login, GroupChat, ZoneId, MessageTime}

import org.apache.spark.sql.Encoder
import org.apache.spark.sql.Encoders._
import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}


/**
 * Encoder may be used if we want encode tupple like:
 *       (
        timestamp,  //  time of receiving message by kafka // Long
        message.chatId, // string
        message.chatName, // string
        message.groupChat, // boolean
        message.zoneId.toString,
        message.utcTime, // sending time by user (do not mistake with timestamp which is time when kafka broker gets message)
        message.content, // string
        message.authorId.toString, // UUID of author converted to string
        message.authorLogin // String
      )
 *
 */
object MessageEncoder {

  type KMessage = (ServerTime, ChatId, ChatName, GroupChat, ZoneId, MessageTime, Content, StrUserID, Login)


  def encoder: Encoder[KMessage] = messageEncoder(scalaLong, STRING, scalaBoolean)


  private def messageEncoder(e1: Encoder[Long], e2: Encoder[String], e3: Encoder[Boolean]): Encoder[KMessage] = {
    ExpressionEncoder.tuple(
      Seq(
        encoderFor(e1), encoderFor(e2), encoderFor(e2), encoderFor(e3), encoderFor(e2),
        encoderFor(e1), encoderFor(e2), encoderFor(e2), encoderFor(e2)
      ) ).asInstanceOf[ExpressionEncoder[KMessage]]
  }

}
