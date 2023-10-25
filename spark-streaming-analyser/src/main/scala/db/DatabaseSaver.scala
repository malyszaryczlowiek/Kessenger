package io.github.malyszaryczlowiek
package db

import java.sql.{Connection, PreparedStatement}
import scala.util.{Failure, Success, Using}

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.Row


abstract class DatabaseSaver(table: DbTable)(implicit connection: Connection) extends Serializable {

  private val logger: Logger = LogManager.getLogger(classOf[DatabaseSaver])


  def createTable() : Int = {
    logger.warn(s"CREATE TABLE '${table.tableName}'")
    // println(s"########## Tworzenie tabeli: ${table.tableName}")
    val sql = s"CREATE TABLE IF NOT EXISTS ${table.tableName} ${table.getTableColumnsWithTypes} "
    Using(connection.prepareStatement(sql)) {
      (statement: PreparedStatement) => statement.executeUpdate()
    } match {
      case Failure(ex) =>
        logger.error(s"ERROR -> CREATE TABLE '${table.tableName}'. Exception:\n${ex.getMessage}")
        // println(s"########## Tworzenie tabeli: ERROR:\n${ex.getMessage}")
        -1
      case Success(v) =>
        logger.warn(s"SUCCESS CREATE TABLE '${table.tableName}'")
        // println(s"########## Tworzenie tabeli: OK")
        v
    }
  }



  def save(r: Row): Unit



}
