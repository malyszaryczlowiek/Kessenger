package util

import io.github.malyszaryczlowiek.kessengerlibrary.model.{ResponseBody, SessionInfo}
import play.api.mvc.Results.{Accepted, Unauthorized}
import play.api.mvc.{Request, Result}

import java.util.UUID
import scala.concurrent.Future

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

class HeadersParser {

  private val logger: Logger = LoggerFactory.getLogger(classOf[HeadersParser]).asInstanceOf[Logger]

  def parseKSID(ksid: String): Option[SessionInfo] = {
    val splitted = ksid.split("__")
    if (splitted.length == 3) {
      try {
        val sessionId = UUID.fromString( splitted(0))
        val userId = UUID.fromString( splitted(1))
        val validityTime = splitted(2).toLong
        Option( SessionInfo(sessionId , userId , validityTime ) )
      } catch {
        case e: Exception => None
        case _: Throwable => None
      }
    } else None
  }

  def processKsid[A](request: Request[A], userId: UUID)(body: (Request[A], SessionInfo ) => Future[Result]): Future[Result] = {
    request.headers.get("KSID") match {
      case Some(ksid) =>
        parseKSID(ksid) match {
          case Some(sessionData) =>
            if (sessionData.userId == userId) {
              logger.trace(s"KSID header parsed correctly. userId(${userId.toString})")
              body(request, sessionData)
            }
            else {
              logger.error(s"Not compatible request path with KSID header. userId(${userId.toString})")
              Future.successful(Unauthorized(ResponseBody(13, "Unauthorized").toString()).discardingHeader("KSID"))
            }
          case None =>
            logger.error(s"Parsing KSID Error. userId(${userId.toString})")
            Future.successful(Unauthorized(ResponseBody(13, "Unauthorized").toString()).discardingHeader("KSID"))
        }
      case None =>
        if (request.path.contains(s"logout")) {
          logger.info(s"Logout call without KSID header. userId(${userId.toString})")
          Future.successful(Accepted(ResponseBody(16, s"Logout accepted without credentials.").toString).discardingHeader("KSID"))
        } else {
          logger.error(s"Logout call without KSID header. userId(${userId.toString})")
          Future.successful(Unauthorized(ResponseBody(13, "Unauthorized").toString()).discardingHeader("KSID"))
        }
    }
  }

  @deprecated
  def parseAuthorization(a: String): Option[(String, String)] = {
    val arr = a.split("\\s") // split with white space
    if ( arr.length != 2) None
    else Option((arr(0), arr(1)))
  }

}
