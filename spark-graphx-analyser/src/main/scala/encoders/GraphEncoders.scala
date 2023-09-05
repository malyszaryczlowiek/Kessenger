package io.github.malyszaryczlowiek
package encoders

import org.apache.spark.graphx.VertexId
import org.apache.spark.sql.Encoder
import org.apache.spark.sql.Encoders._
import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}


object GraphEncoders {

  def vertexEncoder: Encoder[(VertexId, String)] =
    ExpressionEncoder.tuple(Seq(encoderFor(scalaLong), encoderFor(STRING))).asInstanceOf[ExpressionEncoder[(VertexId, String)]]

  def edgeEncoder: Encoder[String] = STRING


}
