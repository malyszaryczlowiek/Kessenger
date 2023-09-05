package io.github.malyszaryczlowiek
package output

// import scala.language.implicitConversions


case class KafkaOutput(key: Array[Byte], value: Array[Byte])

object KafkaOutput {
//  implicit def encoder(e: Encoders.BINARY): Encoder[KafkaOutput] = {
//    ExpressionEncoder.tuple(Seq( encoderFor(e), encoderFor(e2))).asInstanceOf[ExpressionEncoder[KafkaOutput]]
//  }
}
