package com.github.malyszaryczlowiek

import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.messages.Message
import kessengerlibrary.serdes.{MessageSerde, UserSerde}
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}

import org.apache.kafka.clients.admin.{Admin, CreateTopicsResult, NewTopic}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, KStream, Produced}
import org.apache.kafka.streams.scala.serialization.Serdes

import java.util.Properties
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.regex.Pattern
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}

object StreamsChatAnalyser {


  def main(args: Array[String]): Unit =



    // Define properties to kafka broker

    val properties: Properties = new Properties()

    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "chat-analyser")
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092")
    //properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094,localhost:9095")
//    properties.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all")
//    properties.put(StreamsConfig.topicPrefix(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG), 2)
    // properties.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1)


    val adminProperties: Properties = new Properties()
    adminProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092")
    // adminProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094,localhost:9095")

    // we try to create collectiong topic

    Using(Admin.create(adminProperties)) {
      admin =>
        val topicName = "all-messages"
        val partitionsNum: Int       = 1   //configurator.TOPIC_PARTITIONS_NUMBER
        val replicationFactor: Short = 3   // configurator.TOPIC_REPLICATION_FACTOR
        val chatConfig: java.util.Map[String, String] = CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
            TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
          )
        )
        val result: CreateTopicsResult = admin.createTopics(
          java.util.List.of(
            new NewTopic(topicName, partitionsNum, replicationFactor).configs(chatConfig)
          )
        )
        val talkFuture: KafkaFuture[Void] = result.values().get(topicName)

        talkFuture.get//(5L, TimeUnit.SECONDS)

        topicName
    } match {
      case Failure(ex)    =>
        KafkaErrorsHandler.handleWithErrorMessage[Chat](ex) match {
          case Left(kafkaError: KafkaError) =>
            println(s"Cannot create topic. ${kafkaError.description}")
          case Right(value) => // not reachable
        }
      case Success(topic) =>
        println(s"Topic $topic created.")
    }


    var continue = true


    // define serde
    val userSerde:    Serde[User]    = new UserSerde
    val messageSerde: Serde[Message] = new MessageSerde


    // we read from ALL chat topics.
    val pattern: Pattern = Pattern.compile(s"chat--([\\p{Alnum}-]*)")


    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()


    // we define topic we read from
    val source: KStream[User, Message] = builder.stream(pattern)(Consumed `with` (userSerde, messageSerde))


    // we simply print every message
    source.peek((user, message) => println(s"key: $user, value: $message"))


    // define where we write output
    // this topic is created manually in proper docker file
    // due to configuration of kafka brokers with
    // auto.create.topic.enable to false
    source.to("all-messages")(Produced `with` (userSerde, messageSerde))


    // we build topology of the stream
    val topology: Topology = builder.build()


    while (continue) {
      //
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
      Thread.sleep(60_000) // one hour
      streams.close()
      println(s"Streams closed.")
    }

}
