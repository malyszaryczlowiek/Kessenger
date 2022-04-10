package com.github.malyszaryczlowiek
package messages

import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, CreateTopicsResult, DeleteTopicsResult, NewTopic}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig

import java.util.{Collections, Properties, UUID}
import scala.util.Try
import scala.jdk.javaapi.CollectionConverters
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, JoinId, WritingId}


protected object KessengerAdmin {

  val properties = new Properties
  properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9093,localhost:9094")
  private val admin: Admin = Admin.create(properties)


  // topic createtion

  def createNewChat(chatId: ChatId, writingId: WritingId): Try[Any] =
    Try {
      val partitionsNum = 1
      val replicationFactor: Short = 3
      val talkConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
        )
      )
      val whoWriteConfig: java.util.Map[String, String] =
        CollectionConverters.asJava(
          Map(
            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
            TopicConfig.RETENTION_MS_CONFIG -> "1000",   // keeps logs only by 1s.
            TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG -> "1000" // difference to assume that log is too late
          )
        )
      val result: CreateTopicsResult = admin.createTopics(
        java.util.List.of(
          new NewTopic(chatId, partitionsNum, replicationFactor).configs(talkConfig),
          new NewTopic(writingId, 1, 3.shortValue()).configs(whoWriteConfig)
        )
      )
      // Call values() to get the result for a specific topic
      val talkFuture: KafkaFuture[Void] = result.values().get(chatId)
      val whoFuture: KafkaFuture[Void] = result.values().get(writingId)
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


  def createChatProducer(): KafkaProducer[String, String] =
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094")
    properties.put(ProducerConfig.ACKS_CONFIG, "all")  // (1) this configuration specifies the minimum number of replicas that must acknowledge a write for the write to be considered successful
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    // ProducerConfig.RETRIES_CONFIG which is default set to Integer.MAX_VALUE id required by idempotence.
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send message immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](properties)

  def createChatConsumer: KafkaConsumer[String, String] = ???



  def createWritingProducer(): KafkaProducer[String, String] =
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094")
    properties.put(ProducerConfig.ACKS_CONFIG, "0")  // No replica must conferm - send and forget
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](properties)

  def createWritingConsumer: KafkaConsumer[String, String] = ???





  // Joining

  def createJoiningTopic(joinId: JoinId): Try[Any] =
    Try {
      val partitionsNum = 1
      val replicationFactor: Short = 3
      val talkConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
        )
      )
      val joinTopic = new NewTopic(joinId, partitionsNum, replicationFactor).configs(talkConfig)
      val result: CreateTopicsResult = admin.createTopics( java.util.List.of( joinTopic ) )
      val joinFuture: KafkaFuture[Void] = result.values().get(joinId)
      joinFuture.get()
    }


  def createJoiningProducer(): KafkaProducer[String, String] = //???
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094")
    properties.put(ProducerConfig.ACKS_CONFIG, "all")  // all replicas must confirm
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](properties)

  def createJoiningConsumer: KafkaConsumer[String, String] = ???

}
