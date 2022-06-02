package com.github.malyszaryczlowiek
package messages

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, JoinId, UserID, WritingId}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaConfigurator
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorStatus, KafkaErrors, KafkaErrorsHandler}
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, CreateTopicsResult, DeleteTopicsResult, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.{Collections, Properties, UUID}
import scala.util.{Failure, Success, Try}
import scala.jdk.javaapi.CollectionConverters


/**
 *
 *
 * Note:
 * Future implementation will use custom serdes.
 */
object KessengerAdmin {

  private var admin: Admin = _
  private var configurator: KafkaConfigurator = _


  def startAdmin(conf: KafkaConfigurator): Unit =
    configurator = conf
    val properties = new Properties
    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.SERVERS)
    admin = Admin.create(properties)


  /**
   * note we handle TimeoutException but not return it.
   * For user it does not matter if we close this correctly.
   */
  def closeAdmin(): Unit =
    Try { admin.close(Duration.ofMillis(5000)) } match {
      case Failure(_) => {}
      case Success(_) => {}
    }


  // topic creation

  def createNewChat(chat: Chat): Either[KafkaError, Chat] = // , writingId: WritingId
    Try {
      val partitionsNum: Int       = configurator.TOPIC_PARTITIONS_NUMBER
      val replicationFactor: Short = configurator.TOPIC_REPLICATION_FACTOR
      val chatConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
        )
      )
      val result: CreateTopicsResult = admin.createTopics(
        java.util.List.of(
          new NewTopic(chat.chatId, partitionsNum, replicationFactor).configs(chatConfig)
        )
      )
      val talkFuture: KafkaFuture[Void] = result.values().get(chat.chatId)
      talkFuture.get(5L, TimeUnit.SECONDS)
      Right(chat)
    } match {
      case Failure(ex)        => KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
      case Success(rightChat) => rightChat
    }

  /**
   * Method removes topics of selected chat
   * @param chatId
   * @param writingId
   * @return
   */
  @deprecated
  def removeChat(chat: Chat): Either[KafkaError, ChatId] =
    Try {
      val deleteTopicResult: DeleteTopicsResult = admin.deleteTopics(java.util.List.of(chat.chatId))
      val topicMap = CollectionConverters.asScala[String, KafkaFuture[Void]](deleteTopicResult.topicNameValues()).toMap
      if topicMap.nonEmpty && topicMap.size == 1 then
        val optionKafkaFuture = topicMap.get(chat.chatId)
        optionKafkaFuture.get // may throw NoSuchElementException
          .get(5L, TimeUnit.SECONDS) // we give five seconds to complete removing chat
          // may throw  InterruptedException ExecutionException TimeoutException
        Right( topicMap.keys.head )
      else
        Left( KafkaError(KafkaErrorStatus.FatalError, KafkaErrorMessage.UndefinedError) )
    } match {
      case Success(either) => either
      case Failure(ex)     => KafkaErrorsHandler.handleWithErrorMessage[ChatId](ex)
    }

  def createChatProducer: KafkaProducer[String, String] =
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.SERVERS)
    properties.put(ProducerConfig.ACKS_CONFIG, "all")  // (1) this configuration specifies the minimum number of replicas that must acknowledge a write for the write to be considered successful
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    // ProducerConfig.RETRIES_CONFIG which is default set to Integer.MAX_VALUE id required by idempotence.
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send message immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](properties)




  /**
   * Configuration of Topics consumer.
   *
   * Note:
   * groupId of topic is unique to avoid belonging different users to the same consumer group.
   * Otherwise only one consumer (in group) can read from topic (because topic must have only one partition
   * [and three replicas]).
   *
   * Autocomit is set to false because we keep offest in db in users_chat table in users_offset
   * column.
   *
   * @param groupId
   * @return
   */
  def createChatConsumer(groupId: String): KafkaConsumer[String, String] =
    val props: Properties = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG       , configurator.SERVERS)
    props.setProperty(ConsumerConfig.GROUP_ID_CONFIG                , groupId)
    props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG      , "false")
    // props.setProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG , "1000")
    props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG  , "org.apache.kafka.common.serialization.StringDeserializer");
    props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    new KafkaConsumer[String, String](props)






  // Joining

  /**
   * Each user must have unique topic to ask him to join any chat.
   *
   *
   * @param joinId
   * @return
   */
  def createJoiningTopic(userID: UserID): Either[KafkaError, Unit] =
    Try {
      val joinId: JoinId = Domain.generateJoinId(userID)
      val partitionsNum: Int       = configurator.TOPIC_PARTITIONS_NUMBER
      val replicationFactor: Short = configurator.TOPIC_REPLICATION_FACTOR
      val talkConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG   -> "-1" // keep all logs forever
        )
      )
      val joinTopic = new NewTopic(joinId, partitionsNum, replicationFactor).configs(talkConfig)
      val result: CreateTopicsResult    = admin.createTopics(java.util.List.of(joinTopic))
      val joinFuture: KafkaFuture[Void] = result.values().get(joinId)

      // this may block max 10s, it is important when kafka broker is down.
      // normally (when we call get()) it takes ~30s
      joinFuture.get(5_000, TimeUnit.MILLISECONDS)
    } match {
      case Failure(ex) => KafkaErrorsHandler.handleWithErrorMessage[Unit](ex)
      case Success(_)  => Right({}) //val unit: Unit = ()
    }


  def createJoiningProducer(userId: UserID): KafkaProducer[String, String] = //???
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.SERVERS)
    properties.put(ProducerConfig.ACKS_CONFIG, "all")  // all replicas must confirm
    properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, userId.toString)  // idempotence is activated automatically
    // properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](properties)


  /**
   * Note only one user can read per this topic so iit is not required to
   * set group_id.
   * @return
   */
  def createJoiningConsumer(): KafkaConsumer[String, String] =
    val props: Properties = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG       , configurator.SERVERS)
    //props.setProperty(ConsumerConfig.GROUP_ID_CONFIG                , groupId)
    props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG      , "false")
    // props.setProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG , "1000")
    props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG  , "org.apache.kafka.common.serialization.StringDeserializer");
    props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    new KafkaConsumer[String, String](props)

}







//    further implementation used in UI version of program

//      val whoWriteConfig: java.util.Map[String, String] =
//        CollectionConverters.asJava(
//          Map(
//            TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
//            TopicConfig.RETENTION_MS_CONFIG -> "1000",   // keeps logs only by 1s.
//            TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG -> "1000" // difference to assume that log is too late
//          )
//        )
//          ,new NewTopic(writingId, 1, 3.shortValue()).configs(whoWriteConfig)
// Call values() to get the result for a specific topic
// val whoFuture: KafkaFuture[Void] = result.values().get(writingId)
// Call get() to block until the topic creation is complete or has failed
// if creation failed the ExecutionException wraps the underlying cause.
//      whoFuture.get()


//  def removeChat(chatId: ChatId, writingId: WritingId): Map[String, KafkaFuture[Void]] =
//    val deleteTopicResult: DeleteTopicsResult = admin.deleteTopics(java.util.List.of(chatId, writingId))
//    CollectionConverters.asScala[String, KafkaFuture[Void]](deleteTopicResult.topicNameValues()).toMap


//    with UI implementation
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






























