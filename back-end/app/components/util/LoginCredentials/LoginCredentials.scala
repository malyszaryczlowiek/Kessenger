package components.util.LoginCredentials

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

case class LoginCredentials(login: String, pass: String)

object LoginCredentials {

  implicit object encoder extends Encoder[LoginCredentials] {
    override def apply(a: LoginCredentials): Json =
      Json.obj(
        ("login", Json.fromString(a.login)),
        ("pass", Json.fromString(a.pass))
      )
  }


  implicit object decoder extends Decoder[LoginCredentials] {
    override def apply(c: HCursor): Result[LoginCredentials] = {
      for {
        login <- c.downField("login").as[String]
        pass <- c.downField("pass").as[String]
      } yield {
        LoginCredentials(login, pass)
      }
    }

  }

}
