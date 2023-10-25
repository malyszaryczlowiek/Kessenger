package io.github.malyszaryczlowiek
package db.savers

import db.{DatabaseSaver, DbTable}
import parsers.RowParser.avgServerDelayParser

import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.Using


class AvgServerDelayDatabaseSaver(table: DbTable)(implicit connection: Connection) extends DatabaseSaver(table)(connection) {

  override def save(r: Row): Unit = {
    val sql = s"INSERT INTO ${table.tableName} ${table.getTableColumnsNames} VALUES ${table.getQuestionMarks}"
    val w = avgServerDelayParser( r )
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) =>
        statement.setTimestamp(1, w.windowStart)
        statement.setTimestamp(2, w.windowEnd)
        statement.setLong(     3,   w.delayMS)
        statement.executeUpdate()
    }
  }

}
