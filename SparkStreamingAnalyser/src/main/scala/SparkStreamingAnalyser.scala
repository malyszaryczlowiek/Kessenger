package io.github.malyszaryczlowiek

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.sql.functions.{avg, count, window}
import org.apache.kafka.common.config.TopicConfig

import util.AppConfig._
import util.KafkaOutput
import util.Mappers._


import kessengerlibrary.model.{Message, MessagesPerZone, SparkMessage}
import kessengerlibrary.serdes.message.MessageDeserializer
import kessengerlibrary.kafka
import kessengerlibrary.kafka.{Done, TopicCreator, TopicSetup}
import kessengerlibrary.serdes.messagesperzone.MessagesPerZoneSerializer

import com.typesafe.config.{Config, ConfigFactory}

import java.sql.Timestamp
import java.time.Instant
import java.util.Properties




class  SparkStreamingAnalyser

object SparkStreamingAnalyser {


  private val logger: Logger = LogManager.getLogger(classOf[SparkStreamingAnalyser])

  logger.trace(s"SparkStreamingAnalyser application starting.")




  // topic names
//  private val outputTopicName     = s"analysis--num-of-messages-per-1min-per-zone"
//  private val testTopicName       = s"tests--$outputTopicName"
//  private val averageNumTopicName = s"analysis--avg-num-of-messages-in-chat-per-1min-per-zone"

  private val allChatsRegex       = "chat--([\\p{Alnum}-]*)"



  private val topicConfig = Map(
    TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
    TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
  )


  // private val servers   = config.getString(s"kafka-servers")
  // private val fileStore = config.getString(s"file-store")

  private val avgServerDelay       = s"avg-server-delay"
  private val avgServerDelayByUser = s"avg-server-delay-by-user"
  private val avgServerDelayByZone = s"avg-server-delay-by-zone"



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
      TopicSetup(avgServerDelay, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByUserTopic =
      TopicSetup(avgServerDelayByUser, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByZoneTopic =
      TopicSetup(avgServerDelayByZone, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)


    TopicCreator.createTopic(avgServerDelayTopic) match {
      case Done =>
        logger.info(s"Topic '$avgServerDelay' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '$avgServerDelay' failed with error: $error")
    }

    TopicCreator.createTopic( avgServerDelayByUserTopic ) match {
      case Done =>
        logger.info(s"Topic '$avgServerDelayByUser' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '$avgServerDelayByUser' failed with error: $error")
    }


    TopicCreator.createTopic( avgServerDelayByZoneTopic ) match {
      case Done =>
        logger.info(s"Topic '$avgServerDelayByZone' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '$avgServerDelayByZone' failed with error: $error")
    }
  }



  /**
   *
   */
  private def prepareSparkSession: SparkSession = {
    val sparkSession = SparkSession
      .builder
      .appName("SparkStreamingAnalyser")
      // two config below added to solve
      // Initial job has not accepted any resources;
      // check your cluster UI to ensure that workers
      // are registered and have sufficient resources
      //      .config("spark.shuffle.service.enabled", "false")
      //      .config("spark.dynamicAllocation.enabled", "false")
      .master("local[2]")
      //  .master("spark://spark-master:7077")    // option for cluster  spark://spark-master:7077
      .getOrCreate()

    // we initialize shutdownhook only once.
    // if initializeShutDownHook then
    Runtime.getRuntime.addShutdownHook( new Thread("closing_stream_thread") {
      override
      def run(): Unit = {
        logger.warn(s"SparkSession closed from ShutdownHook.")
        sparkSession.close()
      }
    })

//    val context = sparkSession.sparkContext
    // context.setLogLevel("WARN")
    sparkSession
  }



  /**
   *
   */
  private def startAnalysing(): Unit = {
    val inputStream: Dataset[SparkMessage] = readAndDeserializeAllChatsData( prepareSparkSession )
    val byUser = avgServerTimeDelayByUser( inputStream )
    saveStreamToKafka( byUser, avgDelayByUserToKafkaMapper, avgServerDelayByUser)

    //getNumberOfMessagesPerTime( inputStream )
    // getAvgNumOfMessInChatPerZonePerWindowTime( inputStream )
  }



  /**
   *
   */
  private def readAndDeserializeAllChatsData(sparkSession: SparkSession): Dataset[SparkMessage] = {
    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaConfig.servers) //
      .option("subscribePattern",        allChatsRegex) // we subscribe all chat topics
      .load()


    // for implicit encoder for case classes
    import sparkSession.implicits._

    val inputStream = df.map( fromKafkaMapper )

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
        | FORM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql( sql1 )

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy( window($"server_time", "1 minute", "30 seconds") )
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("diff(diff_time_ms)", "avg_diff_time_ms")

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
        | FORM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql(sql1)

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds")
        , $"zone_id" // we grouping via zone
      )
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("diff(diff_time_ms)", "avg_diff_time_ms")

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
        | FORM input_table
        |""".stripMargin

    val s = stream.sqlContext.sql(sql1)

    val s2 = s.withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds")
        , $"user_id" // we grouping via user
      )
      .avg("diff_time_ms") // and count number of messages in every minutes in each zone
      .withColumnRenamed("diff(diff_time_ms)", "avg_diff_time_ms")

    println(s"\n avgServerTimeDelayByUser SCHEMA: \n")
    s2.printSchema()
    // window, user_id, avg_diff_time_ms

    s2.createOrReplaceTempView(s"delay_by_user")

    val sqlSplitter =
      """SELECT
        | date_format(window.start, 'yyyy-MM-dd HH:mm:ss' ) AS window_start,
        | date_format(window.end, 'yyyy-MM-dd HH:mm:ss' ) AS window_end,
        | user_id,
        | avg_diff_time_ms
        | FROM delay_by_user
        |""".stripMargin

    s2.sqlContext.sql( sqlSplitter )
  }




  /*
  średnie opóźnienie serwera względem wysłanej wiadomości w ms
  średnie opóźnienie serwera względem wysłanej wiadomości w ms na zone_id

  wyniki zapisać do
  1. kafki
  2. pliku csv
  3. bazy danych
   */

  /**
   * This method requires input stream as Dataset[Row] (not Dataset[SparkMessage])
   * because Kafka can only read string or binary key-value input.
   */
  private def saveStreamToKafka(stream: Dataset[Row], mapper: Row => KafkaOutput, topic: String): Unit = {
    import stream.sparkSession.implicits._
    stream
      .map( mapper )
      .writeStream
      .outputMode("append") // append us default mode for kafka output
      .format("kafka")
      .option("checkpointLocation",      kafkaConfig.fileStore)
      .option("kafka.bootstrap.servers", kafkaConfig.servers)
      .option("topic", topic)
      .start()
      .awaitTermination()
  }


  /**
   * TODO tutaj jako input stream trzeba dać NIESERIALIZOWANY stream czyli zawierający
   *  wszyskie
   */
  private def saveStreamToDatabase(stream: Dataset[Row], tableNameToSave: String): Unit = {
    val url = s"${dbConfig.protocol}://${dbConfig.server}:${dbConfig.port}/${dbConfig.schema}"
    // import stream.sparkSession.implicits._
    stream
      .write
      .format("jdbc")
      // TODO sprawdzić czy nie trzeba też podać też drivera jako JAR
      //  przy uruchamianiu kontenera w Dockerfile
      //  --driver-class-path postgresql-9.4.1207.jar --jars postgresql-9.4.1207.jar
      // .option("driver", "org.postgresql.Driver")
      .option("url",      url)
      .option("dbtable",  tableNameToSave)
      .option("user",     dbConfig.user)
      .option("password", dbConfig.pass)
      .save()





//    val connectionProperties = new Properties()
//    connectionProperties.put("user",     dbConfig.user)
//    connectionProperties.put("password", dbConfig.pass)
//
//
//    stream.write
//      .option("createTableColumnTypes", "name CHAR(64), comments VARCHAR(1024)") // create new table
//      .jdbc(ERRORR)

  }


  /**
   *
   */
  private def saveStreamToCSV(stream: Dataset[Row], fileName: String): Unit = {
    stream
      .writeStream
      .format("parquet") // can be "orc", "json", "csv", etc.
      // TODO ustawić jeszcze w applicatio.conf jaki folder ma być współdzielony
      //  i do niego zapisywać plik csv.
      .option("path", "path/to/destination/dir")
      .option("sep", "||")
      .start()
  }






























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


    println(s"\n COUNTED SCHEMA: \n")  // todo DELETE for testing
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


    println(s"\n SPLIT COUNTED SCHEMA: \n")  // todo DELETE for testing
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
        s"start: $window_start, end: $window_end, zone: $zone, number: $number" // todo for testing
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
      .option("topic"                  , "foo") // todo this topic does not exist
      .start()
      .awaitTermination()

  }



    // print data to console only appending new data
    // we print each item to console // TODO delete it
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
    //    // TODO DELETE for testing
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
  private def getAvgNumOfMessInChatPerZonePerWindowTime(inputStream: Dataset[SparkMessage]): Unit = {

    import inputStream.sparkSession.implicits._

    // sql aggregation
    // TODO edit zamiast window trzeba napisać całe wyrażenie window(server_time, '1 minute', '30 seconds')
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
      .option("checkpointLocation",      fileStore )
      .option("kafka.bootstrap.servers", servers ) //
      .option("topic",                   "foo") // todo this topic does not exist
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


