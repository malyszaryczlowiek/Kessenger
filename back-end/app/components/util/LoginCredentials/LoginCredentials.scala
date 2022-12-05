package components.util.LoginCredentials

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

import java.util.UUID

case class LoginCredentials(login: String, pass: String, userId: Option[UUID])

object LoginCredentials {

  implicit object encoder extends Encoder[LoginCredentials] {
    override def apply(a: LoginCredentials): Json = {
      a.userId match {
        case Some(userID) =>
          Json.obj(
            ("login",  Json.fromString(a.login)),
            ("pass",   Json.fromString(a.pass)),
            ("userId", Json.fromString(a.userId.toString))
          )
        case None =>
          Json.obj(
            ("login",  Json.fromString(a.login)),
            ("pass",   Json.fromString(a.pass)),
            ("userId", Json.fromString(""))
          )
      }
    }
  }


  implicit object decoder extends Decoder[LoginCredentials] {
    override def apply(c: HCursor): Result[LoginCredentials] = {
      for {
        login  <- c.downField("login") .as[String]
        pass   <- c.downField("pass")  .as[String]
        userId <- c.downField("userId").as[String]
      } yield {
        if (userId.isEmpty) LoginCredentials(login, pass, None)
        else LoginCredentials(login, pass, Option(UUID.fromString(userId)))
      }
    }

  }

}
