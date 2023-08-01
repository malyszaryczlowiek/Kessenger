package io.github.malyszaryczlowiek
package db.savers

import db.{DatabaseSaver, DbTable}
import parsers.RowParser.avgServerDelayParser

import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.Using


class AvgServerDelayDatabaseSaver extends DatabaseSaver {


  override protected def table: DbTable = DbTable("avg_server_delay",
    Map( implement )
  )



  override def save(r: Row)(implicit connection: Connection): Unit = {
    implement // poprawić sql statement
    val sql = s"INSERT INTO ${table.tableName} (session_id, user_id, validity_time)  VALUES (?, ?, ?)"
    val w = avgServerDelayParser( r )
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) =>
        statement.setObject(1, w.windowStart)
        statement.setObject(2, w.windowEnd)
        statement.setLong(3,   w.delayMS)
        statement.executeUpdate()
    }
  }
}
