package io.github.malyszaryczlowiek
package config

import java.sql.{Connection, DriverManager}
import java.util.Properties
import AppConfig.dbConfig
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import scala.util.Using


class Database
object Database {

  private val logger: Logger = LogManager.getLogger(classOf[Database])

  private val dbProps = new Properties
  dbProps.setProperty("user",     dbConfig.user)
  dbProps.setProperty("password", dbConfig.pass)
  Class.forName( dbConfig.driver )

  implicit var connection: Connection = DriverManager.getConnection(dbConfig.dbUrlWithSchema, dbProps)
  logger.trace(s"Database connection enabled.")


  private def closeConnection(): Unit = Using(connection){ connection => connection.close() }

  def restartConnection(): Unit  = {
    closeConnection()
    connection = DriverManager.getConnection(dbConfig.dbUrlWithSchema, dbProps)
  }


  def isConnected: Boolean = connection.isValid(10)


  Runtime.getRuntime.addShutdownHook(new Thread("database_closing_thread") {
    override
    def run(): Unit = {
      closeConnection()
      logger.warn(s"Database connection closed from ShutdownHook.")
    }
  })


}
