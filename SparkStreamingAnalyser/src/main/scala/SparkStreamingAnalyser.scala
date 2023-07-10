package io.github.malyszaryczlowiek

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.sql.functions.{avg, count, window}

import encoders.RichMessage
import kessengerlibrary.model.{Message, MessagesPerZone}
import kessengerlibrary.serdes.message.MessageDeserializer
import kessengerlibrary.kafka.TopicCreator
import kessengerlibrary.env.Prod
import kessengerlibrary.serdes.messagesperzone.MessagesPerZoneSerializer

import com.typesafe.config.{Config, ConfigFactory}

import java.sql.Timestamp
import java.time.Instant




class  SparkStreamingAnalyser

object SparkStreamingAnalyser {


  private val logger: Logger = LogManager.getLogger(classOf[SparkStreamingAnalyser])

  logger.trace(s"SparkStreamingAnalyser application starting.")

  private var config: Config = null
  private val env = System.getenv("SPARK_ENV")

  if (env != null) {
    if (env.equals("PROD"))
      config = ConfigFactory.load("application.conf").getConfig("kessenger.spark-streaming-analyser.prod")
    else
      config = ConfigFactory.load("application.conf").getConfig("kessenger.spark-streaming-analyser.dev")
  } else {
    logger.error(s"No SPARK_ENV environment variable defined. ")
    throw new IllegalStateException("No SPARK_ENV environment variable defined. ")
  }


  // topic names
  val outputTopicName     = s"analysis--num-of-messages-per-1min-per-zone"
  val testTopic           = s"tests--$outputTopicName"
  val averageNumTopicName = s"analysis--avg-num-of-messages-in-chat-per-1min-per-zone"

  val servers       = config.getString("kafka-servers")
  val applicationId = config.getString("application-id")

  def main(args: Array[String]): Unit = {
    println(s"\nApp SparkStreamingAnalyser started 15\n")
    createRequiredTopics()
    startAnalysing()
  }



  /**
   *
   */
  private def createRequiredTopics(): Unit = {
    // In first step we create topic for spark analysis output
    TopicCreator.createTopic( outputTopicName,     Prod )
    TopicCreator.createTopic( testTopic,           Prod )
    TopicCreator.createTopic( averageNumTopicName, Prod )
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
        println(s"SparkSession closed from ShutdownHook.")
        sparkSession.close()
      }
    })

    val context = sparkSession.sparkContext
    // context.setLogLevel("WARN")
    sparkSession
  }



  /**
   *
   */
  private def startAnalysing(): Unit = {
    val inputStream = readAndDeserializeAllChatsData( prepareSparkSession )
    getNumberOfMessagesPerTime( inputStream )
    // getAvgNumOfMessInChatPerZonePerWindowTime( inputStream )
  }



  /**
   *
   */
  private def readAndDeserializeAllChatsData(sparkSession: SparkSession): Dataset[RichMessage] = {
    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("subscribePattern", "chat--([\\p{Alnum}-]*)") // we subscribe all chat topics
      .load()

    // mapper for deserialization of kafka values and mapping
    // to RichMessage case class for easier data manipulation
    val mapper = (r: Row) => {
      val messageByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
      val timestamp: Timestamp  = r.getAs[Timestamp]("timestamp") // .getTime
      // deserializer must be created inside mapper, outer initialization causes exceptions
      val messageDeserializer = new MessageDeserializer
      // we deserialize our message from Array[Byte] to Message object
      val message: Message = messageDeserializer.deserialize("", messageByteArray)
      val messageTime = Timestamp.from(Instant.ofEpochMilli( message.sendingTime ))
      println(s"$timestamp ${message.authorLogin} >> ${message.content}") // for testing TODO delete this line
      RichMessage (
        timestamp,  //  time of receiving message by kafka // Long
        message.chatId, // string
        message.chatName, // string
        message.groupChat, // boolean
        message.zoneId.toString,
        messageTime,
        //        message.utcTime, // sending time by user (do not mistake with timestamp which is time when kafka broker gets message)
        message.content, // string
        message.authorId.toString, // UUID of author converted to string
        message.authorLogin // String
      )
    }

    // for implicit encoder for case classes
    import sparkSession.implicits._

    val inputStream = df.map( mapper )

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

    inputStream
  }  // end readAndDeserializeAllChatsData


  /**
   *
   */
  private def getNumberOfMessagesPerTime(inputStream: Dataset[RichMessage]): Unit = {
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
      .option("checkpointLocation"     , "/opt/FileStore/checkpointDir")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("topic"                  , outputTopicName)
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
  private def getAvgNumOfMessInChatPerZonePerWindowTime(inputStream: Dataset[RichMessage]): Unit = {

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

    println(s"### numOfMessagesPerChatPerZone ###")
    numOfMessagesPerChatPerZone.printSchema()

    numOfMessagesPerChatPerZone.createOrReplaceTempView("average_number_table_0")




    val averageMessageNumberPerTimeAndPerZone = numOfMessagesPerChatPerZone
      .groupBy($"window", $"zone_id")
      .agg(avg($"count").as("avg")) // finally we calculate average message number per chat per zone in window period of time.

    averageMessageNumberPerTimeAndPerZone.createOrReplaceTempView("average_number_table")

    println(s"######### averageMessageNumberPerTimeAndPerZone ######### ")
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
      .option("checkpointLocation", "/opt/FileStore/checkpointDir")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("topic", averageNumTopicName)
      .start()
      .awaitTermination()



    // rozseparować wszystko na oddzielne metody,
    // tak aby każda metoda brała jako swój argument obiekt spark session

    // kożystając z wyżej napisanych części,
    // napisać stream który robi grupowanie po czasie windowingu, zone_id i chat_id
    // sumuje ile w danej strefie czasowej w danym czacie było wiadomości i zapisuje to do
    // oddzielnego topica a następnie inny stream wczytuje te dane i ponownie grupuje
    // je ale tym razem po windowingu i zone_id i liczy średnią z liczby wiadomości
    // na czat na każdą strefę czasową.





    // what implement yet
    // 1. [DONE] ilość wiadomości z danej strefy czasowej w ciągu każdej godziny.
    // 2. średnie opóźnienie czasu servera względem czasu wiadomości ogólnie i względem strefy czasowej
    // 3. [process] średnia ilość wiadomości na chat w danej strefie czasowej na jednostkę czasu np dzień.


  }




} // end SparkStreamingAnalyser


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


