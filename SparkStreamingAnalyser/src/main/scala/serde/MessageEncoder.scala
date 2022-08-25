package com.github.malyszaryczlowiek
package serde

import org.apache.spark.sql.Encoder
import org.apache.spark.sql.Encoders._
import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}

object MessageEncoder {


  def encoder: Encoder[(Long, String, String, String, String, String, Boolean, String, Long)] =
    encoderT(scalaLong, STRING, STRING, STRING, STRING, STRING, scalaBoolean, STRING, scalaLong)



  private def encoderT(e1: Encoder[Long],
              e2: Encoder[String],
              e3: Encoder[String],
              e4: Encoder[String],
              e5: Encoder[String],
              e6: Encoder[String],
              e7: Encoder[Boolean],
              e8: Encoder[String],
              e9: Encoder[Long]): Encoder[(Long, String, String, String, String, String, Boolean, String, Long)] = {
    ExpressionEncoder.tuple(
      Seq(
        encoderFor(e1), encoderFor(e2), encoderFor(e3), encoderFor(e4), encoderFor(e5),
        encoderFor(e6), encoderFor(e7), encoderFor(e8), encoderFor(e9)
      ) ).asInstanceOf[ExpressionEncoder[(Long, String, String, String, String, String, Boolean, String, Long)]]
  }

}
