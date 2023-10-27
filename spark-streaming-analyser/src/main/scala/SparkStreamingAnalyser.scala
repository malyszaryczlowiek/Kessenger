package io.github.malyszaryczlowiek

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

// import config.Database

import config.Database.connection // implicit




class  SparkStreamingAnalyser
object SparkStreamingAnalyser {


  private val logger: Logger = LogManager.getLogger(classOf[SparkStreamingAnalyser])
  logger.trace(s"SparkStreamingAnalyser application starting.")

  private val topicConfig = Map(
    TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
    TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
  )

  private val allChatsRegex        = "chat--([\\p{Alnum}-]*)"



  private val avgServerDelay       = DbTable("avg_server_delay",
    Map(
      "window_start" -> "timestamp",
      "window_end"   -> "timestamp",
      "delay"        -> "BIGINT",
    )
  )

  private val avgServerDelayByUser = DbTable("avg_server_delay_by_user",
    Map(
      "window_start"     -> "timestamp",
      "window_end"       -> "timestamp",
      "user_id"          -> "uuid",
      "avg_diff_time_ms" -> "BIGINT",
    )
  )

  private val avgServerDelayByZone = DbTable(s"avg-server-delay-by-zone",
    Map(
      "window_start"  -> "timestamp",
      "window_end"    -> "timestamp",
      "zone_id"       -> "varchar(255)",
      "delay_by_user" -> "BIGINT",
    )
  )



  def main(args: Array[String]): Unit = {
    logger.trace("App SparkStreamingAnalyser started")
    createRequiredTopics()
    startAnalysing()
  }



  /**
   *
   */
  private def createRequiredTopics(): Unit = {
    // In first step we create topic for spark analysis output

    val avgServerDelayTopic =
      TopicSetup(avgServerDelay.tableName,       kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByUserTopic =
      TopicSetup(avgServerDelayByUser.tableName, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByZoneTopic =
      TopicSetup(avgServerDelayByZone.tableName, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)


    TopicCreator.createTopic(avgServerDelayTopic) match {
      case Done =>
        logger.info(s"Topic '${avgServerDelay.tableName}' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '${avgServerDelay.tableName}' failed with error: $error")
    }

    TopicCreator.createTopic( avgServerDelayByUserTopic ) match {
      case Done =>
        logger.info(s"Topic '${avgServerDelayByUser.tableName}' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '${avgServerDelayByUser.tableName}' failed with error: $error")
    }


    TopicCreator.createTopic( avgServerDelayByZoneTopic ) match {
      case Done =>
        logger.info(s"Topic '${avgServerDelayByZone.tableName}' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '${avgServerDelayByZone.tableName}' failed with error: $error")
    }

    // val db = new Database
  }



  /**
   *
   */
  private def prepareSparkSession: SparkSession = {
    val sparkSession = SparkSession
      .builder
      .appName( appId )
      // two config below added to solve
      // Initial job has not accepted any resources;
      // check your cluster UI to ensure that workers
      // are registered and have sufficient resources
      //      .config("spark.shuffle.service.enabled", "false")
      //      .config("spark.dynamicAllocation.enabled", "false")
      // .master("local[2]")
      .config("spark.worker.cleanup.enabled", "true")
      .master("spark://spark-master:7077")    // option for cluster  spark://spark-master:7077
      .getOrCreate()

    // we initialize shutdownhook only once.
    // if initializeShutDownHook then
    Runtime.getRuntime.addShutdownHook( new Thread("closing_stream_thread") {
      override
      def run(): Unit = {
        logger.warn(s"SparkSession closed from ShutdownHook.")
        // Database.closeConnection()
        sparkSession.close()
      }
    })

    // val context = sparkSession.sparkContext
    // context.setLogLevel("WARN")
    sparkSession
  }



  /**
   *
   */
  private def startAnalysing(): Unit = {
    val inputStream: Dataset[SparkMessage] = readAndDeserializeAllChatsData( prepareSparkSession )
    val avgServerDelayByUserStream = avgServerTimeDelayByUser( inputStream )

    // printing schema of output stream
    // println(s"\n avgServerTimeDelayByUser SCHEMA: \n")
    // avgServerDelayByUserStream.printSchema()

    // save data to proper sinks
    saveStreamToKafka( avgServerDelayByUserStream, avgDelayByUserToKafkaMapper, avgServerDelayByUser.tableName)
    // saveStreamToCSV(  avgServerDelayByUserStream, "analysis" )







    //    inputStream
//      .writeStream
//      .format("console")
//      .start()
//      .awaitTermination()

    // deprecated
    // getNumberOfMessagesPerTime( inputStream )
    // getAvgNumOfMessInChatPerZonePerWindowTime( inputStream )

    //    val saver = new AvgServerDelayByUserDatabaseSaver(avgServerDelayByUser)
    //    val writer = new PostgresWriter(saver)
    //    saveStreamToDatabase(avgServerDelayByUserStream, writer)
  }



  /**
   *
   */
  private def readAndDeserializeAllChatsData(sparkSession: SparkSession): Dataset[SparkMessage] = {
    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaConfig.servers) //
      //.option("subscribePattern",        allChatsRegex) // we subscribe all chat topics
      .option("subscribe", "chat--541e7401-f332-4f21-9e1d-15a616e7ce3c--79df5513-28f5-437a-8ec8-c9e571e1e662")
      // newly added
      // .option("startingOffsets", "earliest")
      //.option("endingOffsets",   "latest")
      .load()


    // for implicit encoder for case classes
    import sparkSession.implicits._

    val inputStream = df.map( kafkaInputRowParser )

    println(s"\n### INITIAL SCHEMA: ###\n")  // todo DELETE for testing
    inputStream.printSchema()
    /*
      root
       |-- server_time: timestamp (nullable = true)
       |-- chat_id: string (nullable = true)
       |-- chat_name: string (nullable = true)
       |-- group_chat: boolean (nullable = false)
       |-- zone_id: string (nullable = true)
       |-- message_time: timestamp (nullable = true)
       |-- content: string (nullable = true)
       |-- user_id: string (nullable = true)
       |-- login: string (nullable = true)
     */
    inputStream.createOrReplaceTempView(s"input_table")
    inputStream
  }  // end readAndDeserializeAllChatsData



  /**
   *
   * @param stream
   * @return
   */
  private def avgServerTimeDelay(stream: Dataset[SparkMessage]): Any = {
    import stream.sparkSession.implicits._

    val sql1 =
      """SELECT
        | server_time, unix_millis(server_time) - unix_millis(message_time) AS diff_time_ms
        | FROM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql( sql1 )

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy( window($"server_time", "1 minute", "30 seconds") )
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
  private def avgServerTimeDelayByZone(stream: Dataset[SparkMessage]): Dataset[Row] = {
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
    // window, zone_id, avg_diff_time_ms

    s2.createOrReplaceTempView(s"delay_by_zone")

    val sqlSplitter =
      """SELECT
        | date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start,
        | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end,
        | zone_id,
        | avg_diff_time_ms
        | FROM delay_by_zone
        |""".stripMargin

    s2.sqlContext.sql(sqlSplitter)

  }


  /**
   *
   * @param stream
   * @return
   */
  private def avgServerTimeDelayByUser(stream: Dataset[SparkMessage]): Dataset[Row] = {
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
    date_format() - zwraca string, stąd error
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

    val s3 = s2.sqlContext.sql( sqlSplitter )
    s3.createOrReplaceTempView(s"avg_server_delay_by_user")
    s3
  }




  /*
  średnie opóźnienie serwera względem wysłanej wiadomości w ms
  średnie opóźnienie serwera względem wysłanej wiadomości w ms na zone_id

  wyniki zapisać do
  1. kafki
  2. pliku csv
  3. bazy danych (nie da się, connection jest nieserializowalne)
   */

  /**
   * This method requires input stream as Dataset[Row] (not Dataset[SparkMessage])
   * because Kafka can only read string or binary key-value input.
   */
  private def saveStreamToKafka(stream: Dataset[Row], mapper: Row => KafkaOutput, topic: String): Unit = {
    import stream.sparkSession.implicits._
    val temp = stream
      .map( mapper )
      .toDF()

    println(s"Final schema to save in kafka")
    temp.printSchema()

    temp.writeStream
      //.outputMode("append") // append is default mode for kafka output
      .format("kafka")
      .option("checkpointLocation",      kafkaConfig.fileStore)
      .option("kafka.bootstrap.servers", kafkaConfig.servers)
      .option("topic",                   topic)
      .start()
      .awaitTermination()
  }




  /**
   * works
   */
  private def saveStreamToCSV(stream: Dataset[Row], subFolder: String): Unit = {
    // filename
    stream
      .writeStream
      .format("csv") // can be "orc", "json", "csv", etc.
      .option("header",   "true")
      .option("encoding", "UTF-8")
      .option("path",     s"$analysisDir/$subFolder")
      .option("sep",      "||")
      .trigger(ProcessingTime( "10 seconds"))
      .outputMode("append")
      .start()
  }





















  /**
   * nierobialne bo zawiera nieserializowalny obiekt jakim jest connection,
   * nie da się go wysłać do różnych partycji
   */
  @deprecated
  private def saveStreamToDatabase(stream: Dataset[Row], writer: ForeachWriter[Row]): Unit = {
    stream
      .writeStream
      .option("checkpointLocation", kafkaConfig.fileStore)
      .foreach(writer)
      //.trigger(Trigger.ProcessingTime("2 seconds"))
      //.outputMode("append")
      .start()
      .awaitTermination()
  }




  //  stream
  //    .write
  //    .format("jdbc")
  //    // TODO sprawdzić czy nie trzeba też podać też drivera jako JAR
  //    //  przy uruchamianiu kontenera w Dockerfile
  //    //  --driver-class-path postgresql-9.4.1207.jar --jars postgresql-9.4.1207.jar
  //    .option("driver", "org.postgresql.Driver")
  //    .option("url", dbConfig.dbUrlWithSchema)
  //    .option("dbtable", saveToTable)
  //    .option("user", dbConfig.user)
  //    .option("password", dbConfig.pass)
  //    .save()

  //    val connectionProperties = new Properties()
  //    connectionProperties.put("user",     dbConfig.user)
  //    connectionProperties.put("password", dbConfig.pass)
  //
  //
  //    stream.write
  //      .option("createTableColumnTypes", "name CHAR(64), comments VARCHAR(1024)") // create new table
  //      .jdbc(ERRORR)



  /**
   *
   */
  @deprecated
  private def getNumberOfMessagesPerTime(inputStream: Dataset[SparkMessage]): Unit = {
    import inputStream.sparkSession.implicits._

    val numMessagesPerMinutePerZone = inputStream
      .withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds"),
        $"zone_id" // we grouping via zone
      )
      .count() // and count number of messages in every minutes in each zone
      .withColumnRenamed("count", "num_of_messages_per_time")


    println(s"\n COUNTED SCHEMA: \n")  //  DELETE for testing
    numMessagesPerMinutePerZone.printSchema()
    /*
    root
      |-- window: struct (nullable = true)
      |    |-- start: timestamp (nullable = true)
      |    |-- end: timestamp (nullable = true)
      |-- zone_id: string (nullable = true)
      |-- num_of_messages_per_time: long (nullable = false)
     */


    numMessagesPerMinutePerZone.createOrReplaceTempView("messages_per_minute_per_zone_table")

    val sqlQuery =
      """SELECT
         | date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start,
         | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end,
         | zone_id AS zone,
         | num_of_messages_per_time
         | FROM messages_per_minute_per_zone_table
        """.stripMargin
    // SORT BY window_start ASC, num_of_messages_per_time DESC

    val numOfMessagesPerZoneWithinWindow = numMessagesPerMinutePerZone.sqlContext
      .sql( sqlQuery )


    println(s"\n SPLIT COUNTED SCHEMA: \n")  //  DELETE for testing
    numOfMessagesPerZoneWithinWindow.printSchema()
    /*
    root
     |-- window_start: string (nullable = true)
     |-- window_end: string (nullable = true)
     |-- zone: string (nullable = true)
     |-- num_of_messages_per_time: long (nullable = false)
     */


    // saving to kafka
    val numOfMessagesPerZoneWithinWindowOutputStream = numOfMessagesPerZoneWithinWindow.map(
      r => {
        // note that in table type is Timestamp but if we want to cast to this type we got
        // exception java.lang.ClassCastException: java.lang.String cannot be cast to java.sql.Timestamp
        val window_start = r.getAs[String]("window_start")
        val window_end   = r.getAs[String]("window_end")
        val zone         = r.getAs[String]("zone")
        val number       = r.getAs[Long](  "num_of_messages_per_time")
        val mesPerZone   = MessagesPerZone(window_start, window_end, zone, number)
        val serializer   = new MessagesPerZoneSerializer
        serializer.serialize("", mesPerZone ) // output is binary
        s"start: $window_start, end: $window_end, zone: $zone, number: $number" //  for testing
      }
    ) // output column name is 'value' so do not need change it.


    println(" ####################    SCHEMA OUTPUT OF NUMBERS    #################### ")
    numOfMessagesPerZoneWithinWindowOutputStream.printSchema()
    /*
    root
      |-- value: binary (nullable = true)
    */


    // save all processed windowed number of messages per zone data to kafka
    numOfMessagesPerZoneWithinWindowOutputStream
      .writeStream
      .outputMode("append")   // append us default mode for kafka output
      .format("kafka")
      .option("checkpointLocation"     , kafkaConfig.fileStore)
      .option("kafka.bootstrap.servers", kafkaConfig.servers) //
      .option("topic"                  , "foo") //  this topic does not exist
      .start()
      .awaitTermination()

  }



    // print data to console only appending new data
    // we print each item to console //  delete it
    //    outputStream
    //      .outputMode("complete") // previous append
    //      .format("console")
    //      .start()
    //      .awaitTermination()


    // path to save results to csv file.
    //    lazy val sqlPath = s"/opt/work-dir/spark-output/analysis/${ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH'h'-mm'm'-ss's'"))}"
    //
    //
    //    val outputStream = numOfMessagesPerZoneWithinWindow
    //      .writeStream
    //
    //
    //    // saving to file
    //    //  DELETE for testing
    //    outputStream
    //      .outputMode("append")
    //      .format("csv")
    //      .option("checkpointLocation", "/opt/FileStore/checkpointDir")
    //      .option("path"              , sqlPath)
    //      .option("sep"               , "\t|\t")
    //      .start()
    //      .awaitTermination()

    /*
    For testing purposes we can send simple string message to kafka broker
    instead of serialized MessagePerZone data.

    val numOfMessagesPerZoneWithinWindowOutputStream = numOfMessagesPerZoneWithinWindow.map(
      r => {
        // note that in table type is Timestamp but if we want to cast to this type we got
        // exception java.lang.ClassCastException: java.lang.String cannot be cast to java.sql.Timestamp
        val window_start = r.getAs[String]("window_start")
        val window_end   = r.getAs[String]("window_end")
        val zone         = r.getAs[String]("zone")
        val number       = r.getAs[Long]("num_of_messages_per_time")
        val mesPerZone   = MessagesPerZone(window_start, window_end, zone, number)
        s"start: $window_start, end: $window_end, zone: $zone, number: $number"
      }
    ) // column name is 'value'

    println("####################    SCHEMA OUTPUT OF NUMBERS    #################### ")
    numOfMessagesPerZoneWithinWindowOutputStream.printSchema()

    // here we send processed data to kafka broker.
    numOfMessagesPerZoneWithinWindowOutputStream
      .writeStream
      .outputMode("append")   // append us default mode for kafka output
      .format("kafka")
      .option("checkpointLocation", "/opt/FileStore/checkpointDir")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("topic", testTopic)        // .option("topic", outputTopicName)
      .start()
      .awaitTermination()

    // finally, checking incoming messages is possible via kafka-console-consumer.sh script
    // docker exec -ti kafka1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic tests--analysis--num-of-messages-per-1min-per-zone --from-beginning --bootstrap-server localhost:9092

     */


  /**
   *
   */
  @deprecated
  private def getAvgNumOfMessInChatPerZonePerWindowTime(inputStream: Dataset[SparkMessage]): Unit = {

    import inputStream.sparkSession.implicits._

    // sql aggregation
    //  edit zamiast window trzeba napisać całe wyrażenie window(server_time, '1 minute', '30 seconds')
    val sql =
    """SELECT date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS from,
      | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS to,
      | zone_id AS zone,
      | count,
      | avg() FILTER(   ) AS avg
      | FROM table
      | GROUP BY GROUPING SET ((window, zone_id, chat_id), (window, zone_id))
      |""".stripMargin


    // now implement point 3

    /*
      Here we count average number of massages in each chat per window time and zone_id
     */
    val numOfMessagesPerChatPerZone = inputStream
      .withWatermark("server_time", "30 seconds")
      .groupBy(
        // for big data better is for example 24h window with 6h slide duration
        // small values below are used for testing/presentation purpose.
        window($"server_time", "1 minute", "30 seconds"),
        $"zone_id"  , // we grouping via zone
        $"chat_id"  // and chat_id
      )
      //.agg( $"zone_id", count )
      //.avg( )//.as("avg")
      .count()
    numOfMessagesPerChatPerZone.printSchema()
    numOfMessagesPerChatPerZone.createOrReplaceTempView("average_number_table_0")




    val averageMessageNumberPerTimeAndPerZone = numOfMessagesPerChatPerZone
      .groupBy($"window", $"zone_id")
      .agg(avg($"count").as("avg")) // finally we calculate average message number per chat per zone in window period of time.
    averageMessageNumberPerTimeAndPerZone.createOrReplaceTempView("average_number_table")
    averageMessageNumberPerTimeAndPerZone.printSchema()




    val sqlQuery = "SELECT date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start, " +
      "date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end, " +
      "zone_id AS zone, " +
      "avg " +
      //"avg(count) AS avg " +
      "FROM average_number_table "

    // split window and rename columns
    val averageMessageNumberPerTimeAndPerZoneOutputStream = numOfMessagesPerChatPerZone.sqlContext
      .sql( sqlQuery )
      .map(
        r => {
          val beginning = r.getAs[String]("window_start")
          val end       = r.getAs[String]("window_end")
          val zone      = r.getAs[String]("zone")
          val avg       = r.getAs[Long]  ("avg")
          s"start: $beginning, end: $end, zone: $zone, avg_num_of_mes_per_chat: $avg"
        })
      .writeStream

    // save to kafka topic
    averageMessageNumberPerTimeAndPerZoneOutputStream
      .outputMode("append")   // append us default mode for kafka output
      .format("kafka")
      .option("checkpointLocation",      kafkaConfig.fileStore )
      .option("kafka.bootstrap.servers", kafkaConfig.servers ) //
      .option("topic",                   "foo") //  this topic does not exist
      .start()
      .awaitTermination()


    // what implement yet
    // 1. [DONE] ilość wiadomości z danej strefy czasowej w ciągu każdej godziny.
    // 2. średnie opóźnienie czasu servera względem czasu wiadomości ogólnie i względem strefy czasowej
    // 3. [process] średnia ilość wiadomości na chat w danej strefie czasowej na jednostkę czasu np dzień.


  }



} // end SparkStreamingAnalyser








/*
// implemented in kafka streams analyser

averageNumberOfWordsInMessageWithin1MinutePerUser

averageNumberOfWordsInMessageWithin1MinutePerZone

averageNumberOfWordsInMessageWithin1MinutePerChat
 */

  /* Util notes

  // to print list of topics
  // docker exec -ti kafka1 /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

  // finally, checking incoming messages is possible via kafka-console-consumer.sh script
  // docker exec -ti kafka1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic analysis--num-of-messages-per-1min-per-zone --from-beginning --bootstrap-server localhost:9092
  */


/*
  to check cluster ui
  http://localhost:8082


  problems
  WARN TaskSchedulerImpl: Initial job has not accepted any resources; check your cluster UI to ensure that workers are registered and have sufficient resources
 */


