package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, JoinId, WritingId}
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaConfigurator
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, CreateTopicsResult, DeleteTopicsResult, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig

import java.util.{Collections, Properties, UUID}
import scala.util.Try
import scala.jdk.javaapi.CollectionConverters


protected object KessengerAdmin {

  var configurator: KafkaConfigurator = _

  def setConfigurator(con: KafkaConfigurator): Unit =
    configurator = con

  val properties = new Properties
  properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094")
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

  def createChatConsumer(groupId: String): KafkaConsumer[String, String] =
    val props: Properties = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG       , "localhost:9093,localhost:9094")
    props.setProperty(ConsumerConfig.GROUP_ID_CONFIG                , groupId)
    props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG      , "false")
    // props.setProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG , "1000")
    props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG  , "org.apache.kafka.common.serialization.StringDeserializer");
    props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    new KafkaConsumer[String, String](props)


//   TODO with UI implementation
//  def createWritingProducer(): KafkaProducer[String, String] =
//    val properties = new Properties
//    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093,localhost:9094")
//    properties.put(ProducerConfig.ACKS_CONFIG, "0")  // No replica must confirm - send and forget
//    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send immediately
//    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
//    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
//    new KafkaProducer[String, String](properties)
//
//  def createWritingConsumer: KafkaConsumer[String, String] = ???





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

  def createJoiningConsumer(): KafkaConsumer[String, String] = ???

}
/*
Uwagi odnośnie consumera:
 1. Consumera trzeba zamknąć po skożystaniu z niego bo w przeciwnym razie połączenie będzie cały czas aktywne
 i będzie blokować dostęp.

 2. Consumer NIE JEST THREAD SAFE.

  group.id określa do jakiej grupy należy consumer i co za tym idzie z jakich partycji będzie zczytywał.



  consumer musi za każdym razem wczytywać info o offsetcie tak aby móc je od razu zapisać do bazy danych.
  Dzięki temu przy następnym wczytaniu danych z kafki możemy dac info do konsumera od której pozycji
  w offsecie powinien zczytywać (pull'ować - metada pull())

  session.timeout.ms - czas  wms jaki consumer ma na wysyłanie heartbeat'u do servera. heartbeat jest wysyłany
     podspodem i my nie mamy możliwości nim sterować.


  max.poll.interval.ms - zapobiega livelock'owi - jest to maxymalny czas w ms po którym consumer jeśli nie wyśle
   pull'a do serwera to zostanie uzanany za martwego i inny consumer z tej samej grupy przechwyci
   partycje należące do martwego.


*/



























