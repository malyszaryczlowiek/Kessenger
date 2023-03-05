package components.db

import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.{KafkaConfigurator, KafkaProductionConfigurator}

import javax.inject.{Inject, Named}


class MyDbExecutor @Inject() (@Named("KafkaProdConf") conf: KafkaConfigurator) extends DbExecutor(conf) {

}