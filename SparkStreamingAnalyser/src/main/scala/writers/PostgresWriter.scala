package io.github.malyszaryczlowiek
package writers

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.{ForeachWriter, Row}


import config.Database
import Database.connection
import db.DatabaseSaver


class PostgresWriter(saver: DatabaseSaver) extends ForeachWriter[Row]{

  private val logger: Logger = LogManager.getLogger(classOf[PostgresWriter])



  override def open(partitionId: Long, epochId: Long): Boolean = {
    val c = Database.isConnected()
    if ( c ) saver.createTable > 0
    else false
  }


  override def process(r: Row): Unit = {
    implement
    saver.save(r)
  }



  override def close(errorOrNull: Throwable): Unit = {
    if (errorOrNull == null) {
      implement
    } else {
      implement
    }
  }
}
