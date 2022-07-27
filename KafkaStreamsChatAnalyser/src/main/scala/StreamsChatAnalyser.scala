package com.github.malyszaryczlowiek

import com.github.malyszaryczlowiek.kessengerlibrary.domain.User
import com.github.malyszaryczlowiek.kessengerlibrary.messages.Message
import com.github.malyszaryczlowiek.kessengerlibrary.serdes.{MessageSerde, UserSerde}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, KStream, Produced}
import org.apache.kafka.streams.scala.serialization.Serdes

import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern

object StreamsChatAnalyser {


  def main(args: Array[String]): Unit =

    // Define properties to kafka broker

    val properties: Properties = new Properties()

    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "chat-analyser")
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092")
//    properties.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all")
//    properties.put(StreamsConfig.topicPrefix(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG), 2)
    // properties.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1)



    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()


    // define serde
    val userSerde: Serde[User] = new UserSerde
    val messageSerde: Serde[Message] = new MessageSerde
    // val stringSerde: Serde[String] = Serdes.stringSerde


    val pattern: Pattern = Pattern.compile(s"chat--([\\p{Alnum}-]*)")


    // TODO change serdes to user and message serdes
    // we define topic we read from
    val source: KStream[User, Message] = builder.stream(pattern)(Consumed `with` (userSerde, messageSerde))


    // we simply print every message
    source.peek((userId, message) => println(s"key: $userId, value: $message"))


    // define where we write output
    // source.to("analysis")(Produced `with` (stringSerde, stringSerde))


    // we build topology of the stream
    val topology: Topology = builder.build()

    var continue = true

    while (continue) {
      //
      val streams: KafkaStreams = new KafkaStreams(topology, properties)

      // val latch: CountDownLatch = new CountDownLatch(1)


      Runtime.getRuntime.addShutdownHook( new Thread("closing_stream_thread") {
        override
        def run(): Unit =
          streams.close()
          println(s"Streams closed.")
          //latch.countDown()
      })

      try {
        streams.start()
        // latch.await()
      } catch {
        case e: Throwable =>
          println(s"ERROR: ${e.toString}")
          continue = false
          System.exit(1)
      }
      println(s"KafkaStreamsChatAnalyser v0.1.1 run correctly !!!")
      Thread.sleep(3600_000)
      streams.close()
      println(s"Streams closed.")
    }



//    System.exit(0)

//    while (true) {
//
//    }



}
