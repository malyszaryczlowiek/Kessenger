package util

import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo
import play.api.mvc.Results.Unauthorized
import play.api.mvc.{Request, Result}

import java.util.UUID
import scala.concurrent.Future

class HeadersParser {

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
            if (sessionData.userId == userId) body(request, sessionData)
            else
              Future.successful(Unauthorized("Error 013. Session not valid. ").discardingHeader("KSID"))
          case None =>
            Future.successful(Unauthorized("Error 013. Session not valid. ").discardingHeader("KSID"))
        }
      case None =>
        Future.successful(Unauthorized("Error 013. Session not valid. ").discardingHeader("KSID"))
    }
  }

  @deprecated
  def parseAuthorization(a: String): Option[(String, String)] = {
    val arr = a.split("\\s") // split with white space
    if ( arr.length != 2) None
    else Option((arr(0), arr(1)))
  }

}
