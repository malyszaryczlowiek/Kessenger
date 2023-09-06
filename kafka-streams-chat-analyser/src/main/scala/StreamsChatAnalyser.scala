package io.github.malyszaryczlowiek


import util.averagenum.{AverageNum, AverageWordsNumSerde}
import kessengerlibrary.model.{Message, User}
import kessengerlibrary.serdes.message.MessageSerde
import kessengerlibrary.serdes.user.UserSerde
import kessengerlibrary.kafka.{Done, Error, TopicCreator, TopicSetup}

import com.typesafe.config.{Config, ConfigFactory}

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.streams.kstream.{Grouped, TimeWindows, Windowed}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.apache.kafka.streams.scala.serialization.Serdes.{intSerde, longSerde, shortSerde, stringSerde}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, KGroupedStream, KStream, KTable, Materialized, Produced}

import java.util.Properties
import java.util.regex.Pattern
import java.time.{Duration => jDuration}
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory


class StreamsChatAnalyser
object StreamsChatAnalyser {

  private val logger: Logger = LoggerFactory.getLogger(classOf[StreamsChatAnalyser]).asInstanceOf[Logger]
  logger.trace(s"StreamsChatAnalyser started.")

  private var config: Config = null
  private val env            = System.getenv("KAFKA_STREAMS_ENV")
  private val confPath       = "application.conf"

  if (env != null) {
    if (env.equals("PROD"))
      config = ConfigFactory.load(confPath).getConfig("kafka.streams.prod")
    else if (env.equals("DEV"))
      config = ConfigFactory.load(confPath).getConfig("kafka.streams.dev")
    else
      // System.exit(1)
      config = ConfigFactory.load(confPath).getConfig("kafka.streams.dev")
  } else
    config = ConfigFactory.load(confPath).getConfig("kafka.streams.dev")
    // System.exit(2)

  logger.warn(s"Current mode: $env")


  private val servers            = config.getString("bootstrap-servers")
  private val applicationId      = config.getString("application-id")
  private val partitionNum       = config.getInt("topic-partition-number")
  private val replicationFac     = config.getInt("topic-replication-factor").toShort
  private val chatPartitionNum   = config.getInt("chat.topic-partition-number")
  private val chatReplicationFac = config.getInt("chat.topic-replication-factor").toShort
  private val stateStore         = config.getString("state-store")

  /*
  Topics that must exists
   */

  /**
   * average number of words in message within 1 minute per user
   */
  private val topic1 = s"average-number-of-words-in-message-within-1-minute-per-user"


  /**
   * average number of words in message in period of time per zone
   */
  private val topic2 = s"average-number-of-words-in-message-within-1-minute-per-zone"


  /**
   * average number of words in message in period of time per chat
   */
  private val topic3 = s"average-number-of-words-in-message-within-1-minute-per-chat"


  /*
  Serializers and deserializers
   */
  private val userSerde    = new UserSerde
  private val messageSerde = new MessageSerde
  private val avgNumSerde  = new AverageWordsNumSerde




  def main(args: Array[String]): Unit = {

    // Define properties for KafkaStreams object
    val properties: Properties = new Properties()
    properties.put( StreamsConfig.APPLICATION_ID_CONFIG,    applicationId)
    properties.put( StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, servers)
    properties.put( StreamsConfig.STATE_DIR_CONFIG,         stateStore)


    /*
    Create required topics to save analyses.
     */
    createRequiredTopics()


    // we will read from ALL chat topics.
    // so we need define pattern to match.
    val pattern: Pattern = Pattern.compile(s"chat--([\\p{Alnum}-]*)")


    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()


    // define serde
    val userSerde:    Serde[User]    = new UserSerde
    val messageSerde: Serde[Message] = new MessageSerde


    // we define topic we read from
    val sourceStream: KStream[User, Message] = builder.stream(pattern)(Consumed `with`(userSerde, messageSerde))


    // for testing purposes
    // we simply print every message
    sourceStream.peek((user, message) => logger.trace(s"User: $user, Message: $message"))


    // analyse data and save to proper topic
    averageNumberOfWordsInMessageWithin1MinutePerUser( sourceStream )
    averageNumberOfWordsInMessageWithin1MinutePerZone( sourceStream )
    averageNumberOfWordsInMessageWithin1MinutePerChat( sourceStream )


    // we build topology of the streams
    val topology: Topology = builder.build()


    /*
      Main loop of program
    */
    var continue = true

    while (continue) {

      // create KafkaStreams object
      val streams: KafkaStreams = new KafkaStreams(topology, properties)

      // val latch: CountDownLatch = new CountDownLatch(1)


      // we initialize shutdownhook only once.
      // if initializeShutDownHook then
      Runtime.getRuntime.addShutdownHook(new Thread("closing_stream_thread") {
        override
        def run(): Unit =
          streams.close()
          logger.warn(s"Streams closed from ShutdownHook.")
        //latch.countDown()
      })
      //initializeShutDownHook = false


      // we starting streams
      try {
        streams.start()
        logger.trace(s"Streams started.")
        // latch.await()
      } catch {
        case e: Throwable =>
          logger.error(s"Error during Streams starting: ${e.toString}.")
          continue = false
          System.exit(1)
      }
      logger.trace(s"KafkaStreamsChatAnalyser started.")
      Thread.sleep(120_000) // two minutes sleep.
      streams.close()
      logger.trace(s"Streams stopped.")
    }

  }




  private def createRequiredTopics(): Unit = {

    val topicConfig = Map(
      TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
      TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
    )

    /*
    We need to create at least one 'foo' topic matching pattern,
    to avoid exception below:
    org.apache.kafka.streams.errors.TaskAssignmentException:
    Failed to compute number of partitions for all repartition topics,
    make sure all user input topics are created and all Pattern subscriptions
    match at least one topic in the cluster
     */
    val nullName = "chat--null"
    val foo = TopicSetup(nullName, servers, chatPartitionNum, chatReplicationFac, topicConfig)

    TopicCreator.createTopic(foo) match {
      case Done =>
        logger.info(s"Topic '$nullName' created")
      case Error(error) =>
        logger.warn(s"Creation topic '$nullName' failed with error: $error")
    }


    // topics to which we save post analysis data
    val t1 = TopicSetup(topic1, servers, partitionNum, replicationFac, topicConfig)
    val t2 = TopicSetup(topic2, servers, partitionNum, replicationFac, topicConfig)
    val t3 = TopicSetup(topic3, servers, partitionNum, replicationFac, topicConfig)


    // we try to create  topics with post analysis data
    TopicCreator.createTopic(t1) match {
      case Done =>
        logger.info(s"Topic '$topic1' created")
      case Error(error) =>
        logger.warn(s"Creation topic '$topic1' failed with error: $error")
    }

    TopicCreator.createTopic(t2) match {
      case Done =>
        logger.info(s"Topic '$topic2' created")
      case Error(error) =>
        logger.warn(s"Creation topic '$topic2' failed with error: $error")
    }

    TopicCreator.createTopic(t3) match {
      case Done =>
        logger.info(s"Topic '$topic3' created")
      case Error(error) =>
        logger.warn(s"Creation topic '$topic3' failed with error: $error")
    }

  }




  private def averageNumberOfWordsInMessageWithin1MinutePerUser(sourceStream: KStream[User, Message]): Unit = {
    sourceStream.mapValues(_.content.trim.split("\\s").length)
      .groupByKey(Grouped.`with`("per-user", userSerde, intSerde) )
      .windowedBy(TimeWindows.ofSizeAndGrace(jDuration.ofSeconds(60L), jDuration.ofMillis(5L)))
      .aggregate(AverageNum())(
        (user, num, agg) => AverageNum.add(agg, num)
      )(Materialized.`with`(userSerde, avgNumSerde) )
      .mapValues(agg => agg.toString())
      .toStream
      .map( (timeKey, value) => (s"${timeKey.window().startTime().toString}___${timeKey.key().userId}", value) )
      .to( topic1 )(Produced.`with`(stringSerde, stringSerde))
  }




  private def averageNumberOfWordsInMessageWithin1MinutePerZone(sourceStream: KStream[User, Message]): Unit = {
    sourceStream.groupBy((u, m) => m.zoneId.getId)(Grouped.`with`("per-zone", stringSerde, messageSerde))
      .windowedBy(TimeWindows.ofSizeAndGrace(jDuration.ofSeconds(60L), jDuration.ofMillis(5L)))
      .aggregate(AverageNum())(
        (zone, message, agg) => AverageNum.add(agg, message.content.trim.split("\\s").length)
      )(Materialized.`with`(stringSerde, avgNumSerde))
      .mapValues(agg => agg.toString())
      .toStream
      .map( (timeKey, value) => (s"${timeKey.window().startTime().toString}___${timeKey.key()}", value) )
      .to( topic2 )(Produced.`with`(stringSerde, stringSerde))
  }




  private def averageNumberOfWordsInMessageWithin1MinutePerChat(sourceStream: KStream[User, Message]): Unit = {
    sourceStream.groupBy((u, m) => m.chatId)(Grouped.`with`("per-chat", stringSerde, messageSerde))
      .windowedBy(TimeWindows.ofSizeAndGrace(jDuration.ofSeconds(60L), jDuration.ofMillis(5L)))
      .aggregate(AverageNum())(
        (chatId, message, agg) => AverageNum.add(agg, message.content.trim.split("\\s").length)
      )(Materialized.`with`(stringSerde, avgNumSerde))
      .mapValues(agg => agg.toString())
      .toStream
      .map( (timeKey, value) => (s"${timeKey.window().startTime().toString}___${timeKey.key()}", value) )
      .to( topic3 )(Produced.`with`(stringSerde, stringSerde))
  }

}

















/*



    // define where we write output
    // this topic is created manually in proper docker file
    // due to configuration of kafka brokers with
    // auto.create.topic.enable to false
    // sourceStream.to("all-messages")(Produced `with` (userSerde, messageSerde))


    // grouping require repartitioning.
    val grouped = Grouped.`with`("starting", stringSerde, messageSerde)


    // we are grouping all messages per sending zone
    val groupedStream: KGroupedStream[String, Message] =
      sourceStream.groupBy((user, message) => message.zoneId.getId)(grouped)


    // and collect only from last 10 seconds
    // and wait 10s for delayed messages.
    // in production we should collect data by one hour or longer period
    val lastFiveMinutes: KTable[Windowed[String], Long] = groupedStream
      .windowedBy(
        TimeWindows.ofSizeAndGrace(
          java.time.Duration.ofSeconds(10), // collect from 10 s
          java.time.Duration.ofSeconds(10)  // wait max 10 s for delayed messages
        )
      )
      .count()(Materialized.as(MESSAGE_NUM_PER_ZONE)(stringSerde, longSerde))


    // convert ktable to kstream (extracting key from Window object)
    val streamToPrint: KStream[String, Long] = lastFiveMinutes.toStream((windowed, long) => windowed.key())



    // print results to docker container console
    // only for testing purposes
    streamToPrint.peek((s, l) => println(s"$s: $l"))


      // and save number of messages per zone in time unit
      // to MESSAGE_NUM_PER_ZONE topic
      .to(MESSAGE_NUM_PER_ZONE)(Produced `with`(stringSerde, longSerde))




 */
