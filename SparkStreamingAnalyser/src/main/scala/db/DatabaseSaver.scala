package io.github.malyszaryczlowiek
package db

import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.{Failure, Success, Using}

trait DatabaseSaver {

  protected def table: DbTable

  def createTable(implicit connection: Connection) : Int = {
    val sql = s"CREATE TABLE IF NOT EXISTS ${table.tableName} ${table.getTableColumnsWithTypes} "
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) => statement.executeUpdate()
    } match {
      case Failure(exception) => 0
      case Success(value) => value
    }
  }




  def save(r: Row)(implicit connection: Connection): Unit

}
