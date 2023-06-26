package kafka

import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{ChatId, JoinId, UserID}
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.errors._
import io.github.malyszaryczlowiek.kessengerlibrary.model._
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.config.TopicConfig
import play.api.inject.ApplicationLifecycle

import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.Future
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Try}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger


/**
 *
 *
 */
@Singleton
class KafkaAdmin @Inject() (@Named("KafkaConfiguration") configurator: KafkaConf, val lifecycle: ApplicationLifecycle) {

  lifecycle.addStopHook { () =>
    Future.successful(this.closeAdmin())
  }

  private val logger: Logger = LoggerFactory.getLogger(classOf[KafkaAdmin]).asInstanceOf[Logger]
  logger.trace(s"KafkaAdmin. Starting KafkaAdmin.")

  private val userSerializer         = configurator.USER_SERIALIZER   // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.user.UserSerializer"
  private val userDeserializer       = configurator.USER_DESERIALIZER // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.user.UserDeserializer"

  private val invitationSerializer   = configurator.INVITATION_SERIALIZER   // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.invitation.InvitationSerializer"
  private val invitationDeserializer = configurator.INVITATION_DESERIALIZER //"io.github.malyszaryczlowiek.kessengerlibrary.serdes.invitation.InvitationDeserializer"

  private val messageSerializer      = configurator.MESSAGE_SERIALIZER   // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.message.MessageSerializer"
  private val messageDeserializer    = configurator.MESSAGE_DESERIALIZER // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.message.MessageDeserializer"

  private val writingSerializer      = configurator.WRITING_SERIALIZER   // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.writing.WritingSerializer"
  private val writingDeserializer    = configurator.WRITING_DESERIALIZER // "io.github.malyszaryczlowiek.kessengerlibrary.serdes.writing.WritingDeserializer"

  private val stringSerializer       = "org.apache.kafka.common.serialization.StringSerializer"
  private val stringDeserializer     = "org.apache.kafka.common.serialization.StringDeserializer"

  private val properties = new Properties
  properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
  // properties.put(AdminClientConfig.CLIENT_ID_CONFIG, tutaj_ID )
  private val admin = Admin.create(properties)



  /**
   * note we handle TimeoutException but not return it.
   * For user it does not matter if we close this correctly.
   */
  def closeAdmin(): Unit = {
    Try {
      if (admin != null) admin.close()
    } match {
      case Failure(ex) =>
        logger.error(s"KafkaAdmin. Exception thrown: ${ex.getMessage}")
      case Success(_) => {}
    }
  }


  def createChat(chat: Chat):  Either[KafkaError, (Boolean, Boolean)] = {
    val cPartitionsNum: Int = configurator.CHAT_TOPIC_PARTITIONS_NUMBER
    val cReplicationFactor: Short = configurator.CHAT_TOPIC_REPLICATION_FACTOR
    val chatConfig: java.util.Map[String, String] = CollectionConverters.asJava(
      Map(
        TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
        TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
      )
    )
    val chatTopic: NewTopic = new NewTopic(chat.chatId, cPartitionsNum, cReplicationFactor).configs(chatConfig)

    val writingId: JoinId = Domain.generateWritingId(chat.chatId)
    val wPartitionsNum: Int = configurator.JOINING_TOPIC_PARTITIONS_NUMBER
    val wReplicationFactor: Short = configurator.JOINING_TOPIC_REPLICATION_FACTOR
    val writingConfig: java.util.Map[String, String] = CollectionConverters.asJava(
      Map(
        TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
        TopicConfig.RETENTION_MS_CONFIG -> "1000", // keeps logs only by 1s.
        TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG -> "1000" // difference to assume that log is too late
      )
    )
    //val n = new NewTopic
    val writingTopic = new NewTopic(writingId, wPartitionsNum, wReplicationFactor).configs(writingConfig)

    val result: CreateTopicsResult = admin.createTopics(java.util.List.of(chatTopic, writingTopic))
    var toReturn = (false, false)
    val chatFuture = result.values().get(chat.chatId)
    val writingFuture = result.values().get(writingId)
    Try {
      chatFuture.get(10L, TimeUnit.SECONDS)
    } match {
      case Failure(ex) =>
        logger.error(s"createChat. Creating chat topic. Exception thrown: ${ex.getMessage}")
      case Success(value) => toReturn = (true, false)
    }
    Try {
      writingFuture.get() // we do not need wait // otherwise 10L, TimeUnit.SECONDS
    } match {
      case Failure(ex) =>
        logger.error(s"createChat. Creating writing topic. Exception thrown: ${ex.getMessage}")
      case Success(value) => toReturn = (toReturn._1, true)
    }

    Right( toReturn )
  }




  /**
   * topic creation
   */
  @deprecated
  def createChatTopic(chat: Chat): Either[KafkaError, Chat] = // , writingId: WritingId
    Try {
      val partitionsNum: Int = configurator.CHAT_TOPIC_PARTITIONS_NUMBER
      val replicationFactor: Short = configurator.CHAT_TOPIC_REPLICATION_FACTOR
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
      case Failure(ex) =>
        logger.error(s"createChatTopic. Exception thrown: ${ex.getMessage}")
        KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
      case Success(rightChat) => rightChat
    }


  /**
   * Method removes topics of selected chat
   *
   * @param chatId
   * @param writingId
   * @return
   */
  def removeChat(chat: Chat): Either[KafkaError, Chat] = {
    Try {
      val wId: JoinId = Domain.generateWritingId(chat.chatId)
      val deleteTopicResult: DeleteTopicsResult = admin.deleteTopics(java.util.List.of(chat.chatId, wId))
      val topicMap = CollectionConverters.asScala[String, KafkaFuture[Void]](deleteTopicResult.topicNameValues()).toMap
      if (topicMap.nonEmpty) {
        // todo uzupełnić o brakujące kafka future.
        val optionKafkaFuture = topicMap.get(chat.chatId)
        optionKafkaFuture.get // may throw NoSuchElementException
          .get(5L, TimeUnit.SECONDS) // we give five seconds to complete removing chat
        // may throw  InterruptedException ExecutionException TimeoutException
        // Right( topicMap.keys.head )
        Right(chat)
      }
      else Left(KafkaError(FatalError, UndefinedError))
    } match {
      case Success(either) => either
      case Failure(ex) =>
        logger.error(s"removeChat. Exception thrown: ${ex.getMessage}")
        KafkaErrorsHandler.handleWithErrorMessage[Chat](ex)
    }
  }


  /**
   * Key is not used in producer, so we default set it to String
   *
   * @return
   */
  def createMessageProducer: KafkaProducer[User, Message] = {
    val properties = new Properties
    // ProducerConfig.RETRIES_CONFIG which is default set to Integer.MAX_VALUE id required by idempotence.
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
    properties.put(ProducerConfig.ACKS_CONFIG, "all") // (1) this configuration specifies the minimum number of replicas that must acknowledge a write for the write to be considered successful
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send message immediately
    //properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG   , "3000") // we try to resend every message via 3000 ms
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   userSerializer) // "org.apache.kafka.common.serialization.StringSerializer"
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, messageSerializer)
    new KafkaProducer[User, Message](properties)
  }


  // for linger ms and delivery timeout
  // delivery.timeout.ms should be equal to or larger than linger.ms + request.timeout.ms.


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
   * Key is not used in consumer, so we default set it to String
   *
   * @param userId this is normally user-id. So all chat users are in different consumer group,
   */
  def createMessageConsumer(userId: String): KafkaConsumer[User, Message] = {
    val props: Properties = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, userId)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, userDeserializer)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, messageDeserializer)
    new KafkaConsumer[User, Message](props)
  }




  // Joining

  /**
   * Each user must have unique topic to ask him to join any chat.
   *
   * @param joinId
   * @return
   */
  def createInvitationTopic(userID: UserID): Either[KafkaError, Unit] = {
    Try {
      val joinId: JoinId = Domain.generateJoinId(userID)
      val partitionsNum: Int = configurator.JOINING_TOPIC_PARTITIONS_NUMBER
      val replicationFactor: Short = configurator.JOINING_TOPIC_REPLICATION_FACTOR
      val talkConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG -> "-1" // keep all logs forever
        )
      )
      val joinTopic = new NewTopic(joinId, partitionsNum, replicationFactor).configs(talkConfig)
      val result: CreateTopicsResult = admin.createTopics(java.util.List.of(joinTopic))
      val joinFuture: KafkaFuture[Void] = result.values().get(joinId)

      // this may block max 10s, it is important when kafka broker is down.
      // normally (when we call get()) it takes ~30s
      joinFuture.get(5_000, TimeUnit.MILLISECONDS)
    } match {
      case Failure(ex) =>
        logger.error(s"createInvitationTopic. Exception thrown: ${ex.getMessage}")
        KafkaErrorsHandler.handleWithErrorMessage[Unit](ex)
      case Success(_) => Right({}) //val unit: Unit = ()
    }
  }


  /**
   * Key is not used in producer, so we default set it to String
   *
   * @param userId
   * @return
   */
  def createInvitationProducer: KafkaProducer[String, Invitation] = {
    val properties = new Properties
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
    properties.put(ProducerConfig.ACKS_CONFIG, "all") // (1) this configuration specifies the minimum number of replicas that must acknowledge a write for the write to be considered successful
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // we do not need duplicates in partitions
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send message immediately
    //properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG   , "3000")   // we try to resend every message via 3000 ms
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, stringSerializer)
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, invitationSerializer)
    new KafkaProducer[String, Invitation](properties)
  }




  /**
   * Note only one user can read per this topic so iit is not required to
   * set group_id.
   *
   * Key is not used in consumer, so we default set it to String
   *
   * @return
   */
  def createInvitationConsumer(groupId: String): KafkaConsumer[String, Invitation] = {
    val props: Properties = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId )
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, stringDeserializer)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, invitationDeserializer)
    new KafkaConsumer[String, Invitation](props)
  }




  // def getStatus: Status = status


  def createWritingTopic(chatId: ChatId): Either[KafkaError, Unit] = {
    Try {
      val joinId: JoinId = Domain.generateWritingId(chatId)
      val partitionsNum: Int = configurator.WRITING_TOPIC_PARTITIONS_NUMBER
      val replicationFactor: Short = configurator.WRITING_TOPIC_REPLICATION_FACTOR
      val talkConfig: java.util.Map[String, String] = CollectionConverters.asJava(
        Map(
          TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_DELETE,
          TopicConfig.RETENTION_MS_CONFIG -> "2000",   // keeps logs only by 2s.
          TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG -> "1500" // difference to assume that log is too late
        )
      )
      val joinTopic = new NewTopic(joinId, partitionsNum, replicationFactor).configs(talkConfig)
      val result: CreateTopicsResult = admin.createTopics(java.util.List.of(joinTopic))
      val joinFuture: KafkaFuture[Void] = result.values().get(joinId)
      // this may block max 5s, it is important when kafka broker is down.
      // normally (when we call get()) it takes ~30s
      joinFuture.get(5_000, TimeUnit.MILLISECONDS)
    } match {
      case Failure(ex) =>
        logger.error(s"createWritingTopic. Exception thrown: ${ex.getMessage}")
        KafkaErrorsHandler.handleWithErrorMessage[Unit](ex)
      case Success(_) => Right({}) //val unit: Unit = ()
    }
  }

  //
  def createWritingProducer: KafkaProducer[String, Writing] = {
    val properties = new Properties
    // properties.put(ProducerConfig.)
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS )
    properties.put(ProducerConfig.ACKS_CONFIG, "0") // No replica must confirm - send and forget
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "0") // we do not wait to fill the buffer and send immediately
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, stringSerializer)
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, writingSerializer)
    new KafkaProducer[String, Writing](properties)
  }



  def createWritingConsumer(userId: String): KafkaConsumer[String, Writing] = {
    val props: Properties = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configurator.BOOTSTRAP_SERVERS)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, userId )
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, stringDeserializer)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, writingDeserializer)
    new KafkaConsumer[String, Writing](props)
  }

}




