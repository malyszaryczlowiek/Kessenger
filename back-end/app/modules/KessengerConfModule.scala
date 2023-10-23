package modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import conf.{KafkaConf, KafkaConfigClass}

class KessengerConfModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[KafkaConf])
      .annotatedWith(Names.named("KafkaConfiguration"))
      .to(classOf[KafkaConfigClass])

//    bind(classOf[Hello])
//      .annotatedWith(Names.named("de"))
//      .to(classOf[GermanHello])
  }

}
