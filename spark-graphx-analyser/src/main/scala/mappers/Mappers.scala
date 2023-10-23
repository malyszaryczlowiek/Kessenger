package io.github.malyszaryczlowiek
package mappers

import org.apache.spark.graphx.{Edge, VertexId, VertexRDD}
import org.apache.spark.sql.Row

class Mappers
object Mappers {

  def toVertex: Row => (VertexId, String) = (r: Row) => {
    val userId    = r.getAs[String](s"uid")
    val userNumId = r.getAs[Long](s"user_num_id")
    ( userNumId, userId)
  }

  def mapToPageRank: Row => (VertexId, Double) = (r: Row) => {
    val userNumId = r.getAs[VertexId](s"uid")
    val pageRank  = r.getAs[Double](s"user_num_id")
    (userNumId, pageRank)
  }

}
