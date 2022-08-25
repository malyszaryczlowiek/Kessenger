package com.github.malyszaryczlowiek

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Column, DataFrame, Dataset, Encoder, Encoders, ForeachWriter, Row, SparkSession}
import serde.{Message, MessageDeserializer, MessageEncoder}

import java.sql.Timestamp


object SparkStreamingAnalyser {

  def main(args: Array[String]): Unit = {

    println(s"\nApp SparkStreamingAnalyser started 14\n")

    Logger.getLogger("org.apache.sparkSession").setLevel(Level.ERROR)

    val sparkSession: SparkSession = SparkSession
      .builder
      .appName("SparkStreamingAnalyser")
      // two config below added to solve
      // Initial job has not accepted any resources; check your cluster UI to ensure that workers are registered and have sufficient resources
//      .config("spark.shuffle.service.enabled", "false")
//      .config("spark.dynamicAllocation.enabled", "false")
      .master("local[2]")
      //.master("spark://spark-master:7077")
      .getOrCreate()


    val context = sparkSession.sparkContext
    context.setLogLevel("WARN")


    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("subscribePattern", "chat--([\\p{Alnum}-]*)") //  // subscribePattern
      .load()


    val mapper = (r: Row) => {
      val messageByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
      val timestamp: Long  = r.getAs[Timestamp]("timestamp").getTime // todo this may cause problems
      val messageDeserializer = new MessageDeserializer
      val message = messageDeserializer.deserialize("", messageByteArray)

      println(s"$timestamp ${message.authorLogin} >> ${message.content}") // for testing

      (
        timestamp,//, // time of receiving message by kafka // Long
        message.chatId, // string
        message.chatName, // string
        message.content, // string
        message.authorId.toString, // UUID of author converted to string
        message.authorLogin, // String
        message.groupChat, // boolean
        message.zoneId.toString, // string
        message.utcTime // sending time by user (do not mistake with timestamp which is time when kafka broker gets message)
      )
    }

    val enc = MessageEncoder.encoder

    val richStream = df.map( mapper )( enc )
      .toDF("server_time", "chat_id", "chat_name", "content", "author_id", "login", "group_chat", "zone", "sending_time" )


   // richStream


    println(s"\n SCHEMA: \n")
    richStream.printSchema()

    richStream
      .writeStream
      .outputMode("append")
      .format("console")
      .start()
      .awaitTermination()





  } // end of main

  // sparkSession.close()

} // end SparkStreamingAnalyser



