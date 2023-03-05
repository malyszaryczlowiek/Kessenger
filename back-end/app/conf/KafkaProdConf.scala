package conf

import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaProductionConfigurator

import javax.inject.Named

@Named("KafkaProdConf")
class KafkaProdConf extends KafkaProductionConfigurator {

  override def CHAT_TOPIC_PARTITIONS_NUMBER: Int = 1

  override def EXTERNAL_SERVERS: String = super.INTERNAL_SERVERS

}
