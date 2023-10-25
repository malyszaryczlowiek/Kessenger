package io.github.malyszaryczlowiek
package writers

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.{ForeachWriter, Row}


import config.Database
//
import db.DatabaseSaver


class PostgresWriter(saver: DatabaseSaver) extends ForeachWriter[Row]{

  private val logger: Logger = LogManager.getLogger(classOf[PostgresWriter])


  override def open(partitionId: Long, epochId: Long): Boolean = {
    val c = Database.isConnected
    if ( c ) {
      logger.warn(s"open(). Connection is opened.")
      saver.createTable >= 0
    }
    else {
      logger.error(s"open(). Connection is still closed.")
      false
    }
  }


  override def process(r: Row): Unit = {
    logger.warn(s"process(r: Row). processing row.")
    saver.save(r)
  }



  override def close(errorOrNull: Throwable): Unit = {
    if (errorOrNull == null) {
      logger.warn(s"close(). Closed normally without exception.")
    } else {
      logger.error(s"close(). Closed with exception:\n${errorOrNull.getMessage}")
    }
  }
}
