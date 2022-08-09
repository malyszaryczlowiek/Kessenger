package com.github.malyszaryczlowiek

import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.messages.Message
import kessengerlibrary.serdes.{MessageSerde, UserSerde}
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}

import util.TopicCreator

import org.apache.kafka.clients.admin.{Admin, CreateTopicsResult, NewTopic}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.{Grouped, TimeWindows, Windowed}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, KGroupedStream, KStream, KTable, Materialized, Produced}
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.scala.serialization.Serdes.{longSerde, stringSerde}

import java.util.Properties
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.regex.Pattern

import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}




object StreamsChatAnalyser {

  /**
   * Topic where we send information of
   * counted messages per zoneid in time unit
   */
  private val MESSAGE_NUM_PER_ZONE = "message-num-per-zone"


  /**
   *
   *
   */
  def main(args: Array[String]): Unit =


    // Define properties for KafkaStreams object
    val properties: Properties = new Properties()
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "chat-analyser")
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092")



    // we try to create collecting topic
    TopicCreator.createTopic( MESSAGE_NUM_PER_ZONE )



    // we will read from ALL chat topics.
    // so we need define pattern to match.
    val pattern: Pattern = Pattern.compile(s"chat--([\\p{Alnum}-]*)")



    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()



    // define serde
    val userSerde:    Serde[User]    = new UserSerde
    val messageSerde: Serde[Message] = new MessageSerde



    // we define topic we read from
    val sourceStream: KStream[User, Message] = builder.stream(pattern)(Consumed `with` (userSerde, messageSerde))



    // for testing purposes
    // we simply print every message
    sourceStream.peek((user, message) => println(s"key: $user, value: $message"))


    // define where we write output
    // this topic is created manually in proper docker file
    // due to configuration of kafka brokers with
    // auto.create.topic.enable to false
    // sourceStream.to("all-messages")(Produced `with` (userSerde, messageSerde))


    // grouping require repartitioning.
    val grouped = Grouped.`with`("repartitioned", stringSerde, messageSerde)



    // we are grouping all messages per sending zone
    val groupedStream: KGroupedStream[String, Message] =
      sourceStream.groupBy((user, message) => message.zoneId.getId)(grouped)



    // and collect only from last 10 seconds and wait 1s for
    // delayed messages.
    // in production we should collect data by one hour or longer period
    val lastFiveMinutes: KTable[Windowed[String], Long] = groupedStream
      .windowedBy(
        TimeWindows.ofSizeAndGrace(
          java.time.Duration.ofSeconds(10),
          java.time.Duration.ofSeconds(10)
        )
      )
      .count()(Materialized.as( MESSAGE_NUM_PER_ZONE )(stringSerde, longSerde))



    // convert ktable to kstream (extracting key from Window object)
    val streamToPrint: KStream[String, Long] = lastFiveMinutes.toStream((windowed, long) => windowed.key())



    // print results to docker container console
    // only for testing purposes
    streamToPrint.peek((s,l) => println(s"$s: $l"))


      // and save number of messages per zone in time unit
      // to MESSAGE_NUM_PER_ZONE topic
      .to( MESSAGE_NUM_PER_ZONE )(Produced `with` (stringSerde, longSerde))



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
        Runtime.getRuntime.addShutdownHook( new Thread("closing_stream_thread") {
          override
          def run(): Unit =
            streams.close()
            println(s"Streams closed from ShutdownHook.")
          //latch.countDown()
        })
        //initializeShutDownHook = false


      // we starting streams
      try {
        streams.start()
        // latch.await()
      } catch {
        case e: Throwable =>
          println(s"ERROR: ${e.toString}")
          continue = false
          System.exit(1)
      }
      println(s"KafkaStreamsChatAnalyser v0.1.1 started correctly !!!")
      Thread.sleep(60_000) // one minute
      streams.close()
      println(s"Streams closed.")
    }

}

/*


<sourceFolder url="file://$MODULE_DIR$/../../src/test/scala" isTestSource="true" packagePrefix="com.github.malyszaryczlowiek" />

*/
