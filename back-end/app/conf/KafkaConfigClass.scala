package conf

import javax.inject.{Inject, Named, Singleton}
import play.api.{ConfigLoader, Configuration}


@Named("KafkaConfiguration")
@Singleton
class KafkaConfigClass @Inject() (conf: Configuration) extends KafkaConf {

  override def BOOTSTRAP_SERVERS: String =
    conf.get("kessenger.kafka.broker.bootstrap_servers")(ConfigLoader.stringLoader)





  override def CHAT_TOPIC_REPLICATION_FACTOR: Short =
    conf.get("kessenger.kafka.broker.topics.chat.replication")(ConfigLoader.numberLoader).shortValue()

  override def CHAT_TOPIC_PARTITIONS_NUMBER: Int =
    conf.get("kessenger.kafka.broker.topics.chat.partition_num")(ConfigLoader.intLoader)

  override def USER_SERIALIZER: String =
    conf.get("kessenger.kafka.user.serializer")(ConfigLoader.stringLoader)

  override def USER_DESERIALIZER: String =
    conf.get("kessenger.kafka.user.deserializer")(ConfigLoader.stringLoader)

  override def MESSAGE_SERIALIZER: String =
    conf.get("kessenger.kafka.chat.serializer")(ConfigLoader.stringLoader)

  override def MESSAGE_DESERIALIZER: String =
    conf.get("kessenger.kafka.chat.deserializer")(ConfigLoader.stringLoader)





  override def JOINING_TOPIC_REPLICATION_FACTOR: Short =
    conf.get("kessenger.kafka.broker.topics.joining.replication")(ConfigLoader.numberLoader).shortValue()

  override def JOINING_TOPIC_PARTITIONS_NUMBER: Int =
    conf.get("kessenger.kafka.broker.topics.joining.partition_num")(ConfigLoader.intLoader)

  override def INVITATION_SERIALIZER: String =
    conf.get("kessenger.kafka.joining.serializer")(ConfigLoader.stringLoader)

  override def INVITATION_DESERIALIZER: String =
    conf.get("kessenger.kafka.joining.deserializer")(ConfigLoader.stringLoader)





  override def WRITING_TOPIC_REPLICATION_FACTOR: Short =
    conf.get("kessenger.kafka.broker.topics.writing.replication")(ConfigLoader.numberLoader).shortValue()

  override def WRITING_TOPIC_PARTITIONS_NUMBER: Int =
    conf.get("kessenger.kafka.broker.topics.writing.partition_num")(ConfigLoader.intLoader)

  override def WRITING_SERIALIZER: String =
    conf.get("kessenger.kafka.writing.serializer")(ConfigLoader.stringLoader)

  override def WRITING_DESERIALIZER: String =
    conf.get("kessenger.kafka.writing.deserializer")(ConfigLoader.stringLoader)



}
