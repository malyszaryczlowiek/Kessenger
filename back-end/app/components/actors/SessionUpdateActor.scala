package components.actors


import akka.actor._
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}
import components.db.DbExecutor
import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo


object SessionUpdateActor {

  def props(db: Database, dbec: ExecutionContext)(implicit configurator: KafkaConf): Props =
    Props(new SessionUpdateActor(db, dbec))

}

class SessionUpdateActor(db: Database, dbec: ExecutionContext)(implicit configurator: KafkaConf) extends Actor{

  println(s"SessionUpdateActor --> started.")


  override def postStop(): Unit = {
    println(s"SessionUpdateActor --> switch off")
  }

  override def receive: Receive = {
    case sessionData: SessionInfo =>
      println(s"SessionUpdateActor --> GOT SESSION UPDATE.")
      Future {
        db.withConnection( implicit connection => {
          val dbExecutor = new DbExecutor(configurator)
          dbExecutor.updateSession(sessionData.sessionId, sessionData.userId, sessionData.validityTime)
          // dbExecutor.removeAllExpiredUserSessions(sessionData.userId, sessionData.validityTime)
        })
      }(dbec)


  }


}


