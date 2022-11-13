package components.actions

import components.db.MyDbExecutor
import components.util.converters.SessionConverter

import play.api.db.Database
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}


case class SessionUpdater @Inject()(parserr: BodyParser[AnyContent])
                                   (
                                     ec: ExecutionContext,
                                     db: Database,
                                     dbExecutor: MyDbExecutor,
                                     sessionConverter: SessionConverter
                                   ) extends ActionBuilder[Request, AnyContent] {


  override protected def executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent] = parserr


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    val newValidityTime = System.currentTimeMillis() / 1000L + 900L
    sessionConverter.convert(request.session) match {
      case Left(response) => Future.successful(response)
      case Right(si) =>
        val f = Future {
          db.withConnection( implicit connection => {
            dbExecutor.updateSession(si.sessionId, si.userId, newValidityTime)
            dbExecutor.removeAllExpiredUserSessions(si.userId)
          })
        }(ec)

        Await.result(f, Duration.create(10L, SECONDS)) match {
          case Left(_)  =>
            block(request)
          case Right(_) =>
            block( request.withHeaders(request.headers.add(("NewValidityTime", s"${newValidityTime.toString}"))))
        }
    }
  }



}

  /*
  try {
        val either = Await.result(f, Duration.create(10L, SECONDS))
        either match {
          case Left(error) => Future.successful(error)
          case Right(n) =>
            if (n == 1) { println(s"Session updated to $newValidityTime")
              block(request.withHeaders(request.headers.add(("NewValidityTime", newValidityTime.toString))))
            } else Future.successful(InternalServerError("Error 021. Other internal Server Error.").withNewSession.discardingHeader("Internal"))
        }

      } catch {
        case e: concurrent.TimeoutException =>
          // note here we do not delete session information
          Future.successful(InternalServerError("Error 022. Timeout Server Error.").discardingHeader("Internal"))
        case _: Throwable =>
          Future.successful(InternalServerError("Error 023. Other internal Server Error.").withNewSession.discardingHeader("Internal"))
      }
   */