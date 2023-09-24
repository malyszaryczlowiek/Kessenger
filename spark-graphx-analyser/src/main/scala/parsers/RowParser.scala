package io.github.malyszaryczlowiek
package parsers

import org.apache.spark.sql.Row

trait RowParser {

  def parse[T](r: Row): T

}
