package io.github.malyszaryczlowiek

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.kafka.common.config.TopicConfig
import org.apache.spark.sql.{Dataset, SparkSession}

import config.AppConfig._
import mappers.KafkaMappers._
import streamsavers.StreamSavers._
import analysers.StreamAnalysers._
import parsers.RowParser.kafkaInputRowParser

import kessengerlibrary.model.SparkMessage
import kessengerlibrary.kafka
import kessengerlibrary.kafka.{Done, TopicCreator, TopicSetup}




class  SparkStreamingAnalyser
object SparkStreamingAnalyser {


  private val logger: Logger = LogManager.getLogger(classOf[SparkStreamingAnalyser])
  logger.trace(s"SparkStreamingAnalyser application starting.")

  private val topicConfig = Map(
    TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
    TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
  )

  // regex of topics to subscribe from
  private val allChatsRegex        = "chat--([\\p{Alnum}-]*)"


  // topics
  private val numOfMessagesPerTime = "num_of_messages_per_1_minute"
  private val avgServerDelay       = "avg_server_delay"
  private val avgServerDelayByUser = "avg_server_delay_by_user"
  private val avgServerDelayByZone = "avg_server_delay_by_zone"



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

    val numOfMessagesPerTimeTopic =
      TopicSetup(numOfMessagesPerTime, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayTopic =
      TopicSetup(avgServerDelay,       kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByUserTopic =
      TopicSetup(avgServerDelayByUser, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)

    val avgServerDelayByZoneTopic =
      TopicSetup(avgServerDelayByZone, kafkaConfig.servers, kafkaConfig.partitionNum, kafkaConfig.replicationFactor, topicConfig)


    TopicCreator.createTopic(numOfMessagesPerTimeTopic) match {
      case Done =>
        logger.info(s"Topic '$numOfMessagesPerTime' created")
      case kafka.Error(error) =>
        logger.error(s"Creation topic '$numOfMessagesPerTime' failed with error: $error")
    }

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
        sparkSession.close()
      }
    })
    sparkSession
  }



  /**
   *
   */
  private def startAnalysing(): Unit = {
    val inputStream: Dataset[SparkMessage] = readAndDeserializeAllChatsData( prepareSparkSession )

    // val numOfMessagesPerTimeStream = StreamAnalysers.numOfMessagesPerTime( inputStream )
    val avgServerDelayByZoneStream = avgServerTimeDelayByZone( inputStream )
    val avgServerDelayByUserStream = avgServerTimeDelayByUser( inputStream )

    // save data to proper sinks
    // saveStreamToKafka( numOfMessagesPerTimeStream, numOfMessagesPerTimeMapper,  numOfMessagesPerTime, false)

    // only one stream is possible to save to kafka at the same time.
    saveStreamToKafka( avgServerDelayByUserStream, avgDelayByUserToKafkaMapper, avgServerDelayByUser, true)
    // saveStreamToKafka( avgServerDelayByZoneStream, avgDelayByZoneToKafkaMapper, avgServerDelayByZone, true)

    // saveStreamToCSV(  avgServerDelayByUserStream, "analysis" )
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
      // .option("startingOffsets", "earliest")
      //.option("endingOffsets",   "latest")
      .load()


    // for implicit encoder for case classes
    import sparkSession.implicits._

    val inputStream = df.map( kafkaInputRowParser )

    println(s"\n### INITIAL SCHEMA: ###\n")
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
  }




}












  /* Util notes

  // to print list of topics in kafka broker
  // docker exec -ti kafka1 /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

  // finally, checking incoming messages is possible via kafka-console-consumer.sh script
  // docker exec -ti kafka1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic analysis--num-of-messages-per-1min-per-zone --from-beginning --bootstrap-server localhost:9092
  */


/*
  to check cluster ui
  http://localhost:8082
 */


