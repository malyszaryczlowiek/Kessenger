package filters

import akka.stream.Materializer
import io.github.malyszaryczlowiek.kessengerlibrary.model.ResponseBody
import play.api.http.HttpEntity
import play.api.mvc.{Filter, RequestHeader, ResponseHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}
import util.JsonParsers


/**
 * Simple header filter which rejects requests without KSID header
 *
 * @param mat
 * @param ec
 */
class KsidFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext, jsonParsers: JsonParsers) extends Filter {


  private val logger: Logger = LoggerFactory.getLogger(classOf[KsidFilter]).asInstanceOf[Logger]


  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    requestHeader.headers.get("KSID") match {
      case Some(_) =>
        logger.trace(s"KSID filter, request has KSID header.")
        nextFilter(requestHeader)
      case None =>
        if (requestHeader.path.contains(s"logout")) {
          logger.trace(s"KSID filter, logout request without KSID header. Passed to nextFilter().")
          // conditionally we pass request which is directed to logout
          nextFilter(requestHeader)
        } else {
          logger.trace(s"KSID filter, request without KSID header. Returned 'Sorry... Request rejected.'")
          Future.successful(
            new Result(ResponseHeader.apply(401, reasonPhrase = Option(ResponseBody(1, "Sorry... Request rejected.").toString)), HttpEntity.NoEntity)
          )
        }

    }
  }


}
