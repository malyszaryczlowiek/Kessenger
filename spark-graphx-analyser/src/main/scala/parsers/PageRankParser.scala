package io.github.malyszaryczlowiek
package parsers

import org.apache.spark.graphx.VertexId
import org.apache.spark.sql.Row

object PageRankParser extends RowParser {

  type PR = (Long, Double)

  def parse[PR](r: Row): PR = {
    val vid      = r.getAs[Long](s"user_num_id")
    val pageRank = r.getAs[Double](s"page_rank")
    (vid, pageRank).asInstanceOf[PR]
  }

}
