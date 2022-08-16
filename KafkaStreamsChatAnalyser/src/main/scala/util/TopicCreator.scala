package com.github.malyszaryczlowiek
package util

import kessengerlibrary.domain.Chat
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorsHandler}
import kessengerlibrary.kafka.configurators.KafkaProductionConfigurator

import org.apache.kafka.clients.admin.{Admin, CreateTopicsResult, NewTopic}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.streams.StreamsConfig

import java.util.Properties
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}

object TopicCreator {

  val adminProperties: Properties = new Properties()
  adminProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092")

  val configurator: KafkaProductionConfigurator = new KafkaProductionConfigurator


  /**
   *
   * @param topicName
   */
  def createTopic(topicName: String): Unit =
    Using(Admin.create(adminProperties)) {
      admin =>

        val partitionsNum: Int       = configurator.CHAT_TOPIC_PARTITIONS_NUMBER
        val replicationFactor: Short = configurator.CHAT_TOPIC_REPLICATION_FACTOR

        val chatConfig: java.util.Map[String, String] = CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
            TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
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
        talkFuture.get//(5L, TimeUnit.SECONDS)

        // simply return topic name as proof of creation
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


}
