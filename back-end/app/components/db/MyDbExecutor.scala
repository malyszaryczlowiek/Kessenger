package components.db

import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaProductionConfigurator


class MyDbExecutor extends DbExecutor(new KafkaProductionConfigurator) {

}