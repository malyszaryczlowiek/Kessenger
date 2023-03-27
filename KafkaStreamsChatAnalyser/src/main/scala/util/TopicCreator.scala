package io.github.malyszaryczlowiek
package util

import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}

import org.apache.kafka.clients.admin.{Admin, CreateTopicsResult, NewTopic}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.streams.StreamsConfig

import java.util.Properties
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}

import com.typesafe.config.Config




object TopicCreator {

  /**
   *
   * @param topicName
   */
  def createTopic(topicName: String, config: Config): Unit = {
    val servers = config.getString("bootstrap-servers")
    val adminProperties: Properties = new Properties()
    adminProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, servers)
    Using(Admin.create(adminProperties)) {
      admin =>
        val partitionsNum: Int       = config.getInt("chat-topic-partition-number")
        val replicationFactor: Short = config.getInt("chat-topic-replication-factor").toShort
        val chatConfig: java.util.Map[String, String] = CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
            TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
          )
        )
        // we create  topic
        val result: CreateTopicsResult = admin.createTopics(
          java.util.List.of(
            new NewTopic(topicName, partitionsNum, replicationFactor).configs(chatConfig)
          )
        )
        // extract task of topic creation
        val talkFuture: KafkaFuture[Void] = result.values().get(topicName)
        // we wait patiently to create topic or get error.
        talkFuture.get //(5L, TimeUnit.SECONDS)
        // simply return topic name as proof of creation
        topicName
    } match {
      case Failure(ex) =>
        KafkaErrorsHandler.handleWithErrorMessage[Unit](ex) match {
          case Left(kafkaError: KafkaError) =>
            println(s"Cannot create topic. ${kafkaError.description}")
          case Right(value) => // not reachable
        }
      case Success(topic) =>
        println(s"Topic $topic created.")
    }


  }

}



/*



ERROR StatusLogger Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console...

ERROR StatusLogger Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console...

ERROR StatusLogger Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console...

ERROR StatusLogger Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console...

Topic message-num-per-zone created.

Streams closed from ShutdownHook.

ERROR: java.lang.IllegalStateException: Stream-client chat-analyser-ff954e95-b7f0-447b-9611-32ed1ff2180b: Unexpected state transition from NOT_RUNNING to REBALANCING

Cannot create topic. Chat already exists Error.

Streams closed from ShutdownHook.

ERROR: java.lang.IllegalStateException: Stream-client chat-analyser-ff954e95-b7f0-447b-9611-32ed1ff2180b: Unexpected state transition from NOT_RUNNING to REBALANCING

Cannot create topic. Chat already exists Error.

Streams closed from ShutdownHook.

ERROR: java.lang.IllegalStateException: Stream-client chat-analyser-ff954e95-b7f0-447b-9611-32ed1ff2180b: Unexpected state transition from NOT_RUNNING to REBALANCING

Cannot create topic. Chat already exists Error.
 */