package io.github.malyszaryczlowiek
package db.savers

import db.{DatabaseSaver, DbTable}
import parsers.PageRankParser.parse

import parsers.PageRankParser.PR
import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.Using

class PageRankSaver(table: DbTable) extends DatabaseSaver(table) {

  override def save(r: Row)(implicit connection: Connection): Unit = {
    val sql = s"INSERT INTO ${table.tableName} ${table.getTableColumnsNames} VALUES ${table.getQuestionMarks}"
    val w: PR = parse(r)
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) =>
        statement.setLong(  1, w._1)
        statement.setDouble(2, w._2)
        statement.executeUpdate()
    }

  }


}
