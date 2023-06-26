package components.actors


import akka.actor._
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}
import components.db.DbExecutor
import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import java.util.UUID


object SessionUpdateActor {

  def props(db: Database, dbec: ExecutionContext, actorGroupID: UUID)(implicit configurator: KafkaConf): Props =
    Props(new SessionUpdateActor(db, dbec, actorGroupID))

}

class SessionUpdateActor(db: Database, dbec: ExecutionContext, actorGroupID: UUID)(implicit configurator: KafkaConf) extends Actor{

  private val logger: Logger = LoggerFactory.getLogger(classOf[SessionUpdateActor]).asInstanceOf[Logger]
  logger.trace(s"SessionUpdateActor. Starting actor. actorGroupID(${actorGroupID.toString})")


  override def postStop(): Unit = {
    logger.trace(s"SessionUpdateActor. Stopping actor. actorGroupID(${actorGroupID.toString})")
  }

  override def receive: Receive = {
    case sessionData: SessionInfo =>
      logger.trace(s"SessionUpdateActor. Updating session info. actorGroupID(${actorGroupID.toString})")
      Future {
        db.withConnection( implicit connection => {
          val dbExecutor = new DbExecutor(configurator)
          dbExecutor.updateSession(sessionData.sessionId, sessionData.userId, sessionData.validityTime)
          // dbExecutor.removeAllExpiredUserSessions(sessionData.userId, sessionData.validityTime)
        })
      }(dbec)


  }


}


