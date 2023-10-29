package io.github.malyszaryczlowiek
package analysers

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.kafka.common.config.TopicConfig
import org.apache.spark.sql.{Dataset, ForeachWriter, Row, SparkSession}
import org.apache.spark.sql.functions.{avg, count, window}
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.streaming.Trigger.ProcessingTime
import config.AppConfig._
import db.savers.AvgServerDelayByUserDatabaseSaver
import db.DbTable
import mappers.KafkaMappers._
import output.KafkaOutput
import parsers.RowParser.kafkaInputRowParser
import writers.PostgresWriter
import kessengerlibrary.model.{MessagesPerZone, SparkMessage}
import kessengerlibrary.kafka
import kessengerlibrary.kafka.{Done, TopicCreator, TopicSetup}
import kessengerlibrary.serdes.messagesperzone.MessagesPerZoneSerializer





object StreamAnalysers {


  /**
   *
   * @param stream
   * @return
   */
  def avgServerTimeDelay(stream: Dataset[SparkMessage]): Any = {
    import stream.sparkSession.implicits._

    val sql1 =
      """SELECT
        | server_time, unix_millis(server_time) - unix_millis(message_time) AS diff_time_ms
        | FROM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql(sql1)

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy(window($"server_time", "1 minute", "30 seconds"))
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("avg(diff_time_ms)", "avg_diff_time_ms")

    println(s"\n avgServerTimeDelay SCHEMA: \n")
    s2.printSchema()
    // window, avg_diff_time_ms

    s2.createOrReplaceTempView(s"server_delay")

    val sqlSplitter =
      """SELECT
        | date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start,
        | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end,
        | avg_diff_time_ms
        | FROM server_delay
        |""".stripMargin

    s2.sqlContext.sql(sqlSplitter)
  }





  /**
   *
   * @param stream
   * @return
   */
  def avgServerTimeDelayByUser(stream: Dataset[SparkMessage]): Dataset[Row] = {
    import stream.sparkSession.implicits._

    val sql1 =
      """SELECT
        | user_id, server_time, unix_millis(server_time) - unix_millis(message_time) AS diff_time_ms
        | FROM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql(sql1)

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds")
        , $"user_id" // we grouping via user
      )
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("avg(diff_time_ms)", "avg_diff_time_ms")

    s2.createOrReplaceTempView(s"delay_by_user")

    println(s"Avg server time delay by user delay_by_user")
    s2.printSchema()
    /*
    date_format() - zwraca string a nie typ TimeStamp, stąd
    exception java.lang.ClassCastException: java.lang.String cannot be cast to java.sql.Timestamp
    | date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start,
    | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end,
     */


    val sqlSplitter =
      """SELECT
        | window.start AS window_start,
        | window.end   AS window_end,
        | user_id,
        | avg_diff_time_ms
        | FROM delay_by_user
        |""".stripMargin
    // java_method('java.util.UUID', 'fromString', user_id) TODO zamienić na tę metodę jak będę chciał uuid zamiast string

    val s3 = s2.sqlContext.sql(sqlSplitter)
    s3.createOrReplaceTempView(s"avg_server_delay_by_user")
    s3
  }





  /**
   *
   * @param stream
   * @return
   */
  def avgServerTimeDelayByZone(stream: Dataset[SparkMessage]): Dataset[Row] = {
    import stream.sparkSession.implicits._

    val sql1 =
      """SELECT
        | zone_id, server_time, unix_millis(server_time) - unix_millis(message_time) AS diff_time_ms
        | FROM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql(sql1)

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds")
        , $"zone_id" // we grouping via zone
      )
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("avg(diff_time_ms)", "avg_diff_time_ms")

    println(s"\n avgServerTimeDelayByZone SCHEMA: \n")
    s2.printSchema()

    s2.createOrReplaceTempView(s"delay_by_zone")


    val sqlSplitter =
      """SELECT
        | window.start AS window_start,
        | window.end   AS window_end,
        | zone_id,
        | avg_diff_time_ms
        | FROM delay_by_zone
        |""".stripMargin

    val s3 = s2.sqlContext.sql(sqlSplitter)
    s3.createOrReplaceTempView(s"avg_server_delay_by_zone")
    s3
  }







  def numOfMessagesPerTime(stream: Dataset[SparkMessage]): Dataset[Row] = {
    import stream.sparkSession.implicits._

    val numMessagesPerMinutePerZone = stream
      .withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds")
      )
      .count() // and count number of messages in every minutes in each zone
      .withColumnRenamed("count", "num_of_messages_per_time")


    println(s"\n COUNTED SCHEMA: \n") //  DELETE for testing
    numMessagesPerMinutePerZone.printSchema()
    /*
    root
      |-- window: struct (nullable = true)
      |    |-- start: timestamp (nullable = true)
      |    |-- end: timestamp (nullable = true)
      |-- num_of_messages_per_time: long (nullable = false)
     */


    numMessagesPerMinutePerZone.createOrReplaceTempView("windowed_num_of_messages")
    val sqlQuery =
      """SELECT
        | window.start AS window_start,
        | window.end   AS window_end,
        | num_of_messages_per_time
        | FROM windowed_num_of_messages
    """.stripMargin

    val numOfMessagesWithinTimeWindow = numMessagesPerMinutePerZone.sqlContext
      .sql(sqlQuery)


    println(s"\n SPLIT COUNTED SCHEMA: \n") //  DELETE for testing
    numOfMessagesWithinTimeWindow.printSchema()
    /*
    root
     |-- window_start: string (nullable = true)
     |-- window_end: string (nullable = true)
     |-- num_of_messages_per_time: long (nullable = false)
     */
    numOfMessagesWithinTimeWindow.createOrReplaceTempView(s"num_of_messages")
    numOfMessagesWithinTimeWindow
  }



}












































