package com.github.malyszaryczlowiek

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, KStream, Produced}
import org.apache.kafka.streams.scala.serialization.Serdes

import java.util.Properties

object StreamsChatAnalyser {


  def main(args: Array[String]): Unit =

    // Define properties to kafka broker

    val properties: Properties = new Properties()

    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "chat-analyser")
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    properties.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all")
    properties.put(StreamsConfig.topicPrefix(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG), 2)
    // properties.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1)



    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()


    // define serde
    val stringSerde: Serde[String] = Serdes.stringSerde


    // we define topic we read from
    val source: KStream[String, String] = builder.stream("chat-*")(Consumed `with` (stringSerde, stringSerde))


    // we simply print every message
    source.peek((userId, message) => println(s"key: $userId, value: $message"))


    // define where we write output
    source.to("analysis")(Produced `with` (stringSerde, stringSerde))


    // we build topology of the stream
    val topology: Topology = builder.build()


    //
    val streams: KafkaStreams = new KafkaStreams(topology, properties)


    Runtime.getRuntime.addShutdownHook( new Thread("closing_stream_thread") {
      override
      def run(): Unit = streams.close()
    })

    try {
      streams.start()
    } catch {
      case e: Throwable =>
        System.exit(1)
    }

    System.exit(0)

//    while (true) {
//      Thread.sleep(2000)
//      println(s"KafkaStreamsChatAnalyser built and run correctly !!!")
//    }



}
