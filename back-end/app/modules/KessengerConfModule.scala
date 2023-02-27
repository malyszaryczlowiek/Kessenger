package modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import conf.KafkaProdConf
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaConfigurator

class KessengerConfModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[KafkaConfigurator])
      .annotatedWith(Names.named("KafkaProdConf"))
      .to(classOf[KafkaProdConf])

//    bind(classOf[Hello])
//      .annotatedWith(Names.named("de"))
//      .to(classOf[GermanHello])
  }

}
