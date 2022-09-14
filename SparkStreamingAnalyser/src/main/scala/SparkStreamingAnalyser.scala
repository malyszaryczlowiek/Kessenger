package io.github.malyszaryczlowiek

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Row, SparkSession}
import encoders.{MessageEncoder, RichMessage}

import java.sql.Timestamp
import kessengerlibrary.messages.Message
import kessengerlibrary.serdes.message.MessageDeserializer
import kessengerlibrary.kafka.TopicCreator
import kessengerlibrary.env.Prod

import org.apache.spark.sql.functions.window

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

object SparkStreamingAnalyser {

  def main(args: Array[String]): Unit = {

    println(s"\nApp SparkStreamingAnalyser started 15\n")

    val outputTopicName = "analysis--num-of-messages-per-1min-per-zone"

    // In first step we create topic for spark analysis output
    TopicCreator.createTopic( outputTopicName, Prod )




    Logger.getLogger("org.apache.sparkSession").setLevel(Level.ERROR)

    val sparkSession: SparkSession = SparkSession
      .builder
      .appName("SparkStreamingAnalyser")
      // two config below added to solve
      // Initial job has not accepted any resources; check your cluster UI to ensure that workers are registered and have sufficient resources
      //      .config("spark.shuffle.service.enabled", "false")
      //      .config("spark.dynamicAllocation.enabled", "false")
      .master("local[2]")
      // .master("spark://spark-master:7077")    // option for cluster
      .getOrCreate()


    val context = sparkSession.sparkContext
    context.setLogLevel("WARN")


    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("subscribePattern", "chat--([\\p{Alnum}-]*)") // we subscribe all chat topics
      .load()


    // mapper for deserialization of kafka values and
    // conversion for easier data manipulation
    val mapper = (r: Row) => {
      val messageByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
      val timestamp: Timestamp  = r.getAs[Timestamp]("timestamp") // .getTime
      // deserializer must be created inside mapper, outer initialization causes exceptions
      val messageDeserializer = new MessageDeserializer
      // we deserialize our message from Array[Byte] to Message object
      val message: Message = messageDeserializer.deserialize("", messageByteArray)

      val messageTime = Timestamp.from(Instant.ofEpochMilli( message.utcTime ))

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

    import sparkSession.implicits._ // for implicit encoder for case classes

    val messagesStream = df.map( mapper )


    println(s"\n INITIAL SCHEMA: \n")  // todo DELETE for testing
    messagesStream.printSchema()




    //
    val numMessagesPerMinutePerZone = messagesStream
      .withWatermark("server_time", "30 seconds")
      .groupBy(
        window($"server_time", "1 minute", "30 seconds"),
        $"zone_id" // we grouping via zone
      )
      .count() // and count number of messages in every minutes
      .withColumnRenamed("count", "num_of_messages_per_time")


    println(s"\n COUNTED SCHEMA: \n")  // todo DELETE for testing
    numMessagesPerMinutePerZone.printSchema()



    numMessagesPerMinutePerZone.createOrReplaceTempView("messages_per_minute_per_zone_table")

    val sortedOutputStream =  numMessagesPerMinutePerZone.sqlContext
      .sql("SELECT date_format(window.start, 'yyyy-MM-dd hh:mm:ss' ) AS window_start, date_format(window.end, 'yyyy-MM-dd hh:mm:ss' ) AS window_end, zone_id AS zone, num_of_messages_per_time FROM messages_per_minute_per_zone_table ")
        // SORT BY window_start ASC, num_of_messages_per_time DESC

    println(s"\n SORTED SCHEMA: \n")  // todo DELETE for testing
    sortedOutputStream.printSchema()


    val outputStream = sortedOutputStream
      .writeStream


    // print data to console only appending new data
    // we print each item to console // TODO delete it
//    outputStream
//      .outputMode("complete") // previous append
//      .format("console")
//      .start()
//      .awaitTermination()


    // path to save results to csv file.
    lazy val sqlPath = s"/opt/work-dir/spark-output/analysis/${ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH'h'-mm'm'-ss's'"))}"


    // saving to file
    // TODO DELETE for testing
    outputStream
      .outputMode("append")
      .format("csv")
      .option("checkpointLocation", "/opt/FileStore/checkpointDir")
      .option("path"              , sqlPath)
      .option("sep"               , "\t|\t")
      .start()
      .awaitTermination()



    // docker exec -ti kafka1  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list








    // saving to kafka
//    outputStream
//      // .outputMode("append")   // append us default mode for kafka output
//      .format("kafka")
//      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
//      .option("topic", outputTopicName)





    // what implement yet
    // 1. ilość wiadomości z danej strefy czasowej w ciągu każdej godziny.
    // 2. średnie opóźnienie czasu servera względem czasu wiadomości ogólnie i względem strefy czasowej
    // 3. średnia ilość wiadomości na chat w danej strefie czasowej na jednostkę czasu np dzień.






  } // end of main

  // sparkSession.close()

} // end SparkStreamingAnalyser



