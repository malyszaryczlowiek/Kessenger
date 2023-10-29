package io.github.malyszaryczlowiek
package streamsavers

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.spark.sql.{Dataset, ForeachWriter, Row}
import org.apache.spark.sql.streaming.Trigger.ProcessingTime
import config.AppConfig._
import output.KafkaOutput



object StreamSavers {


  /**
   * This method requires input stream as Dataset[Row] (not Dataset[SparkMessage])
   * because Kafka can only read string or binary key-value input.
   */
  def saveStreamToKafka(stream: Dataset[Row], mapper: Row => KafkaOutput, topic: String, awaitTermination: Boolean): Unit = {
    import stream.sparkSession.implicits._
    val temp = stream
      .map(mapper)
      .toDF()

    println(s"Final schema to save in kafka")
    temp.printSchema()

    val savingStream = temp.writeStream
      //.outputMode("append") // append is default mode for kafka output
      .format("kafka")
      .option("checkpointLocation", kafkaConfig.fileStore)
      .option("kafka.bootstrap.servers", kafkaConfig.servers)
      .option("topic", topic)
      .start()

    if ( awaitTermination ) savingStream.awaitTermination()
  }


  /**
   * works
   */
  def saveStreamToCSV(stream: Dataset[Row], path: String): Unit = {
    // filename
    stream
      .writeStream
      .format("csv") // can be "orc", "json", "csv", etc.
      .option("header",   "true")
      .option("encoding", "UTF-8")
      .option("path",     s"$path")
      // .option("path", s"$analysisDir/$subFolder")
      .option("sep",      "||")
      .trigger(ProcessingTime("10 seconds"))
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



}
