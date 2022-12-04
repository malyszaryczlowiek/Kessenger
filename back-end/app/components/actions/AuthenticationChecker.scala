package components.actions

import components.db.MyDbExecutor
import play.api.db.Database
import play.api.mvc.Results.Unauthorized
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import util.HeadersParser

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class AuthenticationChecker @Inject()(parserr: BodyParser[AnyContent])
                                     (ec: ExecutionContext,
                                      db: Database,
                                      dbExecutor: MyDbExecutor,
                                      headerParser: HeadersParser) extends ActionBuilder[Request, AnyContent]{

  override def parser: BodyParser[AnyContent] = parserr
  override protected def executionContext: ExecutionContext = ec


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(value) =>
        headerParser.parseAuthorization(value) match {
          case Some((login, pass)) => ???

          case None => Future.successful(Unauthorized)
        }
      case None => Future.successful(Unauthorized)
    }
  }


}
