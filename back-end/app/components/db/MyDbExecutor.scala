package components.db

import conf.KafkaConf
import javax.inject.{Inject, Named}


class MyDbExecutor @Inject() (@Named("KafkaConfiguration") conf: KafkaConf) extends DbExecutor(conf) {

}