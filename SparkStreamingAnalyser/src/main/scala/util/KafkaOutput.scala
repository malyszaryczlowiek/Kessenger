package io.github.malyszaryczlowiek
package util

import org.apache.spark.sql.{Encoder, Encoders}
import org.apache.spark.sql.Encoders._
import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}

// import scala.language.implicitConversions


case class KafkaOutput(key: Array[Byte], value: Array[Byte])

object KafkaOutput {
  implicit def encoder(e: Encoders.BINARY): Encoder[KafkaOutput] = {
    ExpressionEncoder.tuple(Seq( encoderFor(e), encoderFor(e2))).asInstanceOf[ExpressionEncoder[KafkaOutput]]
  }
}
