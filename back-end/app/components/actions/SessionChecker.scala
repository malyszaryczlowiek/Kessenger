package components.actions

import components.db.MyDbExecutor
import components.util.converters.SessionConverter
import models.ResponseErrorBody
import play.api.db.Database
import play.api.mvc.Results._
import play.api.mvc._
import util.HeadersParser

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}


case class SessionChecker @Inject()(parserr: BodyParser[AnyContent], userId: UUID)
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
        val f: Future[Either[Result, Boolean]] = Future {
          db.withConnection(implicit connection => {
            dbExecutor.checkUsersSession(sessionData.sessionId, sessionData.userId, System.currentTimeMillis()) match {
              case Left(_) => Left(InternalServerError("Error 016. Session parsing error. "))
              case Right(i) => Right(i)
            }
          })
        }(ec)
        try {
          val either = Await.result(f, Duration.create(10L, SECONDS))
          either match {
            case Left(error) => Future.successful(error)
            case Right(b) =>
              if (b) block(req)
              else Future.successful(Unauthorized(ResponseErrorBody(19, "Session timeout").toString))
          }
        } catch {
          case e: concurrent.TimeoutException =>
            // note here we do not delete session information
            Future.successful(InternalServerError("Error 017. Timeout Server Error."))
          case _: Throwable =>
            Future.successful(InternalServerError("Error 018. Other internal Server Error."))
        }
      }
    }
  }



}

