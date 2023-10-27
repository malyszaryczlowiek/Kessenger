package io.github.malyszaryczlowiek
package output

import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}
import org.apache.spark.sql.{Encoder, Encoders }




case class KafkaOutput(key: Array[Byte], value: Array[Byte])

object KafkaOutput {

//  def encoder: Encoder[KafkaOutput] = {
//    ExpressionEncoder.tuple(Seq( encoderFor( Encoders.BINARY ), encoderFor( Encoders.BINARY ))).asInstanceOf[ExpressionEncoder[KafkaOutput]]
//  }

}
