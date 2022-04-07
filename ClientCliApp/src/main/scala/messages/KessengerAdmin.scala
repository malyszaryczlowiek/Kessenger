package com.github.malyszaryczlowiek
package messages

import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, CreateTopicsResult, DeleteTopicsResult, NewTopic}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig

import java.util.{Collections, Properties}
import scala.util.Try
import scala.jdk.javaapi.CollectionConverters
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, WritingId}


protected object KessengerAdmin {

  val properties = new Properties
  properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9093")
  properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9094")
  private val admin: Admin = Admin.create(properties)


  def createNewChat(topics: (ChatId, WritingId)): Try[Any] =
    Try {
      val partitionsNum = 3
      val replicationFactor: Short = 3

      val talkConfig: java.util.Map[String, String] =
        CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_COMPACT,
            TopicConfig.RETENTION_MS_CONFIG -> "-1"
          )
        )

      val whoWriteConfig: java.util.Map[String, String] =
        CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
            TopicConfig.RETENTION_MS_CONFIG -> "1000"
            // ,TopicConfig.DELETE_RETENTION_MS_CONFIG -> "1000"
          )
        )

      val (talkTopicName, whoWriteTopicName) = topics

      val result: CreateTopicsResult = admin.createTopics(
        java.util.List.of(
          new NewTopic(talkTopicName, partitionsNum, replicationFactor)
            .configs(talkConfig),
          new NewTopic(whoWriteTopicName, 1, 3.shortValue())
            .configs(whoWriteConfig)
        )
      )

      // Call values() to get the result for a specific topic
      val talkFuture: KafkaFuture[Void] = result.values().get(talkTopicName)
      val whoFuture: KafkaFuture[Void] = result.values().get(whoWriteTopicName)

      // Call get() to block until the topic creation is complete or has failed
      // if creation failed the ExecutionException wraps the underlying cause.
      talkFuture.get()
      whoFuture.get()
    }


  /**
   * Method removes topics of selected talk
   * @param chatId
   * @param writingId
   * @return
   */
  def removeChat(chatId: ChatId, writingId: WritingId): Map[String, KafkaFuture[Void]] =
    val deleteTopicResult: DeleteTopicsResult = admin.deleteTopics(java.util.List.of(chatId, writingId))
    CollectionConverters.asScala[String, KafkaFuture[Void]](deleteTopicResult.topicNameValues()).toMap


  def createProducer: KafkaProducer[User, Message] =
    val properties = new Properties


    new KafkaProducer[User, Message](properties)

  def createConsumer(chatId: ChatId): KafkaConsumer[User, Message] = ???




}
