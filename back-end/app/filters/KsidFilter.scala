package filters

import akka.stream.Materializer
import play.api.http.HttpEntity
import play.api.mvc.{Filter, RequestHeader, ResponseHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


/**
 * Simple header filter which rejects requests without KSID filter
 * @param mat
 * @param ec
 */
class KsidFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {


  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    requestHeader.headers.get("KSID") match {
      case Some(_) => nextFilter(requestHeader)
      case None =>
        Future.successful(
          new Result(ResponseHeader.apply(401, reasonPhrase = Option("Sorry... Request rejected.")), HttpEntity.NoEntity)
        )
    }
  }



}
