package components.actions

import components.db.MyDbExecutor
import components.util.converters.SessionConverter
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.UserID
import play.api.db.Database
import play.api.mvc._
import util.HeadersParser

import javax.inject.Inject
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}


case class SessionUpdater @Inject()(parserr: BodyParser[AnyContent], userId: UserID)
                                   (
                                     ec: ExecutionContext,
                                     db: Database,
                                     dbExecutor: MyDbExecutor,
                                     headerParser: HeadersParser
                                   ) extends ActionBuilder[Request, AnyContent] {


  override protected def executionContext: ExecutionContext = ec

  override def parser: BodyParser[AnyContent] = parserr


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    headerParser.processKsid(request, userId) {
      (req, sessionData) => {
        val f = Future {
          db.withConnection(implicit connection => {
            dbExecutor.updateSession(sessionData.sessionId, sessionData.userId, sessionData.validityTime)
            dbExecutor.removeAllExpiredUserSessions( userId, sessionData.validityTime )
          })
        }(ec)
        Await.result(f, Duration.create(10L, SECONDS)) match {
          case Left(_) =>
            block(request)
          case Right(_) =>
            block(request)
        }
      }
    }
  }

}

