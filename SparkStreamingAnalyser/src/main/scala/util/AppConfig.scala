package io.github.malyszaryczlowiek
package util


import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import com.typesafe.config.{Config, ConfigFactory}


class AppConfig
object AppConfig {

  private val logger: Logger = LogManager.getLogger(classOf[AppConfig])

  logger.trace(s"AppConfig started.")

  private var config: Config = null
  private val env = System.getenv("SPARK_ENV")

  if (env != null) {
    if (env.equals("PROD")) {
      logger.trace(s"Loading PROD configuration.")
      config = ConfigFactory.load("application.conf").getConfig("kessenger.spark-streaming-analyser.prod")
    } else {
      logger.trace(s"Loading DEV configuration.")
      config = ConfigFactory.load("application.conf").getConfig("kessenger.spark-streaming-analyser.dev")
    }
  } else {
    logger.error(s"No SPARK_ENV environment variable defined. ")
    throw new IllegalStateException("No SPARK_ENV environment variable defined. ")
  }

  // "jdbc:postgresql://localhost:5438/kessenger_schema"
  private case class DbConfig(protocol: String, server: String, port: Int, schema: String, user: String, pass: String)

  val dbConfig: DbConfig = DbConfig(
    config.getString(s"db.protocol"),
    config.getString(s"db.server"),
    config.getInt(s"db.port"),
    config.getString(s"db.schema"),
    config.getString(s"db.user"),
    config.getString(s"db.pass")
  )

  private case class KafkaConfig(servers: String, fileStore: String, partitionNum: Int, replicationFactor: Short)

  val kafkaConfig: KafkaConfig = KafkaConfig(
    config.getString(s"kafka-servers"),
    config.getString(s"file-store"),
    config.getInt("topic-partition-num"),
    config.getInt("topic-replication-factor").toShort
  )


}
