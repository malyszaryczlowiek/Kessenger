package com.github.malyszaryczlowiek

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Column, DataFrame, Dataset, Encoder, Encoders, ForeachWriter, Row, SparkSession}
import serde.MessageDeserializer
import java.sql.Timestamp


object SparkStreamingAnalyser {

  def main(args: Array[String]): Unit = {

    println(s"\nApp SparkStreamingAnalyser started 12\n")

    Logger.getLogger("org.apache.sparkSession").setLevel(Level.ERROR)

    val sparkSession: SparkSession = SparkSession
      .builder
      .appName("SparkStreamingAnalyser")
      .master("local")
      //.master("spark://spark-master:7077")
      // .config()
      .getOrCreate()


    val context = sparkSession.sparkContext
    context.setLogLevel("WARN")


    val df = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092") //
      .option("subscribePattern", "chat--([\\p{Alnum}-]*)") //  // subscribePattern
      .load()

    val encoderTuple = Encoders.tuple(Encoders.scalaLong, Encoders.STRING, Encoders.STRING)//new CustomEncoder

    val mapper = (r: Row) => {
      val messageByteArray: Array[Byte] = r.getAs[Array[Byte]]("value") // we know that value is Array[Byte] type
      val timestamp: Long  = r.getAs[Timestamp]("timestamp").getTime // todo this may cause problems
      // val s = new String(messageByteArray)
      // println(s"\nDECODED STRING: $s")
      val messageDeserializer = new MessageDeserializer
      val message = messageDeserializer.deserialize("", messageByteArray)

      println(s"$timestamp ${message.authorLogin} >> ${message.content}")

      // tuple to return
      (
        timestamp,//, // time of receiving message by kafka // Long
        //        message.chatId, // string
        //        message.chatName, // string
        message.content, // string
        //        message.authorId.toString, // UUID of author converted to string
        message.authorLogin // String
        //        message.groupChat, // boolean
        //        message.zoneId.toString, // string
        //        message.utcTime // sending time by user (do not mistake with timestamp which is time when kafka broker gets message)
        )
    }


    val readStream = df.map( mapper )(encoderTuple)//(Encoders.scalaLong)//(encoderTuple)
      .toDF("timestamp", "content", "author_login")


    println(s"\n SCHEMA: \n")
    readStream.printSchema()


    readStream.writeStream
      .outputMode("append")
      .format("console")
      .start()
      .awaitTermination()



  } // end of main

  // sparkSession.close()

} // end SparkStreamingAnalyser



