package components.db

// import io.github.malyszaryczlowiek.kessengerlibrary.db.DbExecutor
import io.github.malyszaryczlowiek.kessengerlibrary.db.queries.{DataProcessingError, ERROR, LoginTaken, QueryError}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{DbResponse, Password}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.{SessionInfo, Settings, User}
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaProductionConfigurator

import java.sql.{Connection, Savepoint, Statement}
import scala.util.{Failure, Success, Using}


class MyDbExecutor extends DbExecutor(new KafkaProductionConfigurator) {

}