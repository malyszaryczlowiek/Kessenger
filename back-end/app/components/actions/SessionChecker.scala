package components.actions

import components.db.MyDbExecutor

import io.github.malyszaryczlowiek.kessengerlibrary.model.ResponseBody

import play.api.db.Database
import play.api.mvc.Results._
import play.api.mvc._
import util.HeadersParser

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}



case class SessionChecker @Inject()(parserr: BodyParser[AnyContent], userId: UUID)
                                   (
                                     ec: ExecutionContext,
                                     db: Database,
                                     dbExecutor: MyDbExecutor,
                                     headerParser: HeadersParser
                                   ) extends ActionBuilder[Request, AnyContent] {



  override protected def executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent] = parserr

  private val logger: Logger = LoggerFactory.getLogger(classOf[SessionChecker]).asInstanceOf[Logger]
  // logger.setLevel(Level.TRACE)


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    headerParser.processKsid(request, userId) {
      (req, sessionData) => {
        val f: Future[Either[Result, Boolean]] = Future {
          db.withConnection(implicit connection => {
            dbExecutor.checkUsersSession(sessionData.sessionId, sessionData.userId, System.currentTimeMillis()) match {
              case Left(e)  =>
                logger.error(s"invokeBlock. Error during DB calling: ${e.description.toString()}. userId($userId)")
                Left(InternalServerError(ResponseBody(16, "Database Error").toString))
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
              else {
                logger.trace(s"invokeBlock. Session Timeout. userId($userId)")
                Future.successful(Unauthorized(ResponseBody(19, "Session timeout").toString).discardingHeader("KSID"))
              }
          }
        } catch {
          case _: concurrent.TimeoutException =>
            logger.warn(s"invokeBlock. Database Timeout Error. userId($userId)")
            Future.successful(InternalServerError(ResponseBody(17, "Database Timeout Error").toString))
          case ee: Throwable =>
            logger.error(s"invokeBlock. Unknown Error: ${ee.getMessage}. userId($userId)")
            Future.successful(InternalServerError(ResponseBody(18, "Internal Server Error").toString))
        }
      }
    }
  }



}

