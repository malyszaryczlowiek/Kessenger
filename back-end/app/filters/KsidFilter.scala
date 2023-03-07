package filters

import akka.stream.Materializer
import components.util.converters.JsonParsers
import io.github.malyszaryczlowiek.kessengerlibrary.model.ResponseBody
import play.api.http.HttpEntity
import play.api.mvc.{Filter, RequestHeader, ResponseHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


/**
 * Simple header filter which rejects requests without KSID header
 * @param mat
 * @param ec
 */
class KsidFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext, jsonParsers: JsonParsers) extends Filter {


  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    requestHeader.headers.get("KSID") match {
      case Some(_) => nextFilter(requestHeader)
      case None =>
        Future.successful(
          new Result(ResponseHeader.apply(401, reasonPhrase = Option(ResponseBody(1, "Sorry... Request rejected.").toString  )), HttpEntity.NoEntity)
        )
    }
  }


}
