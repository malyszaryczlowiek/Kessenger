package io.github.malyszaryczlowiek
package db

import org.apache.spark.sql.Row

import java.sql.{Connection, PreparedStatement}
import scala.util.{Failure, Success, Using}

abstract class DatabaseSaver(table: DbTable) extends Serializable {


  def createTable(implicit connection: Connection) : Int = {
    println(s"########## Tworzenie tabeli: ${table.tableName}")
    val sql = s"CREATE TABLE IF NOT EXISTS ${table.tableName} ${table.getTableColumnsWithTypes} "
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) => statement.executeUpdate()
    } match {
      case Failure(ex) =>
        println(s"########## Tworzenie tabeli: ERROR:\n${ex.getMessage}")
        0
      case Success(v ) =>
        println(s"########## Tworzenie tabeli: OK")
        v
    }
  }



  def save(r: Row)(implicit connection: Connection): Unit

}
