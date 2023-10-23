package io.github.malyszaryczlowiek
package db.savers

import db.{DatabaseSaver, DbTable}


import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.Using


class AvgServerDelayDatabaseSaver(table: DbTable) extends DatabaseSaver(table) {

  override def save(r: Row)(implicit connection: Connection): Unit = ???
//  {
//    val sql = s"INSERT INTO ${table.tableName} ${table.getTableColumnsNames} VALUES ${table.getQuestionMarks}"
//    val w = avgServerDelayParser( r )
//    Using(connection.prepareStatement(sql)) {
//      (statement: PreparedStatement) =>
//        statement.setTimestamp(1, w.windowStart)
//        statement.setTimestamp(2, w.windowEnd)
//        statement.setLong(     3,   w.delayMS)
//        statement.executeUpdate()
//    }
//  }

}
