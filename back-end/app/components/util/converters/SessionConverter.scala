package components.util.converters

import io.github.malyszaryczlowiek.kessengerlibrary.model.SessionInfo

import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.mvc.Results._

import java.util.UUID

class SessionConverter {

  def convert(s: Session): Either[Result, SessionInfo] = {
    try {
      val ses = SessionInfo(
        UUID.fromString(s.data.getOrElse("session_id", "")),
        UUID.fromString(s.data.getOrElse("user_id", "")),
        s.data.getOrElse("validity_time", "").toLong
      )
      Right(ses)
    }
    catch {
      case _: Throwable => Left(InternalServerError("Error 015. Session parsing error. ").withNewSession.discardingHeader("Internal"))
    }
  }



  def updateSession(request: Request[AnyContent]): Session = {
    request.headers.get("NewValidityTime") match {
      case Some(value) =>
        request.headers.remove("NewValidityTime")
        request.session + ("validity_time", value)
      case None =>
        request.session
    }
  }




}
