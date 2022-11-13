package components.actions

import components.db.MyDbExecutor
import components.util.converters.SessionConverter

import play.api.db.Database
import play.api.mvc.Results._
import play.api.mvc._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}


case class SessionChecker @Inject()(parserr: BodyParser[AnyContent], userId: UUID)
                                   (
                                     implicit val ec: ExecutionContext,
                                     db: Database,
                                     dbExecutor: MyDbExecutor,
                                     sessionConverter: SessionConverter
                                   ) extends ActionBuilder[Request, AnyContent] {



  override protected def executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent] = parserr


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    val session = request.session
    if (session.isEmpty || session.data.getOrElse("validity_time", "0").toLong <= (System.currentTimeMillis() / 1000L)) {
      Future.successful(Unauthorized("Error 013. Session not valid. ")
        .withNewSession
        .discardingHeader("Internal")
      )
    }
    else {
      // user id's must be equal
      val fromSession = session.get("user_id").getOrElse("")
      val fromPath    = userId.toString
      if ( fromSession == fromPath ) {
        sessionConverter.convert(session) match {
          case Left(response) => Future.successful(response)
          case Right(si) =>
            if (request.headers.hasHeader("Internal")) block(request.withHeaders(request.headers.remove("Internal")))
            else {
              // if we do not call this endpoint internally from redirection from this server we need check session in db
              val f: Future[Either[Result, Int]] = Future {
                db.withConnection(implicit connection => {
                  dbExecutor.checkUsersSession(si.sessionId, si.userId, si.validityTime) match {
                    case Left(_)  => Left(InternalServerError("Error 016. Session parsing error. "))
                    case Right(i) => Right(i)
                  }
                })
              }
              try {
                val either = Await.result(f, Duration.create(10L, SECONDS))
                either match {
                  case Left(error) => Future.successful(error)
                  case Right(n) =>
                    if (n == 1) block(request)
                    else Future.successful( InternalServerError("Error 019. Other internal Server Error.").withNewSession )
                }

              } catch {
                case e: concurrent.TimeoutException =>
                  // note here we do not delete session information
                  Future.successful( InternalServerError("Error 017. Timeout Server Error.") )
                case _: Throwable =>
                  Future.successful( InternalServerError("Error 018. Other internal Server Error.").withNewSession )
              }
            }
        }
      } else {
        Future.successful(
          Unauthorized("Error 014. Users not matching. ")
            .withNewSession
            .discardingHeader("Internal")
        )
      }
    }
  }



}
