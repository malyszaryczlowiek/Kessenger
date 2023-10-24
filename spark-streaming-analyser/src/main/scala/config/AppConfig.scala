package io.github.malyszaryczlowiek
package config


import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import com.typesafe.config.{Config, ConfigFactory}


class AppConfig
object AppConfig {

  private val logger: Logger = LogManager.getLogger(classOf[AppConfig])

  logger.trace(s"AppConfig started.")

  private val config: Config = ConfigFactory.load("application.conf").getConfig("kessenger.spark-streaming-analyser")
  logger.trace(s"Loading configuration from application.conf.")


  // "jdbc:postgresql://localhost:5438/kessenger_schema"
  case class DbConfig(driver: String, protocol: String, server: String, port: Int, schema: String, user: String, pass: String, dbUrlWithSchema: String )

  val dbConfig: DbConfig = DbConfig(
    config.getString(s"db.driver"),
    config.getString(s"db.protocol"),
    config.getString(s"db.server"),
    config.getInt(s"db.port"),
    config.getString(s"db.schema"),
    config.getString(s"db.user"),
    config.getString(s"db.pass"),
    s"${config.getString(s"db.protocol")}://${config.getString(s"db.server")}:${config.getInt(s"db.port")}/${config.getString(s"db.schema")}"
  )

  case class KafkaConfig(servers: String, fileStore: String, partitionNum: Int, replicationFactor: Short)

  val kafkaConfig: KafkaConfig = KafkaConfig(
    config.getString(s"kafka-servers"),
    config.getString(s"file-store"),
    config.getInt("topic-partition-num"),
    config.getInt("topic-replication-factor").toShort
  )

  val analysisDir: String = config.getString("output-analysis-dir")


  val appId: String = config.getString(s"application-id")


}
