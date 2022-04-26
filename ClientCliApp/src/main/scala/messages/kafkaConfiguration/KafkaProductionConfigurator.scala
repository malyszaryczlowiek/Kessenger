package com.github.malyszaryczlowiek
package messages.kafkaConfiguration

class KafkaProductionConfigurator extends KafkaConfigurator:
  override def SERVERS: String = "localhost:9093,localhost:9094,localhost:9095"

  // topics
  override def TOPIC_REPLICATION_FACTOR: Short = 3

