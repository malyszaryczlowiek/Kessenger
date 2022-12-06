package models

// import io.circe.Decoder.Result

//import io.circe.{Decoder, Encoder, Error, HCursor, Json}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
// import models.ResponseErrorBody.encoder

case class ResponseErrorBody(num: Int, message: String) {
  override def toString: String = this.copy().asJson(ResponseErrorBody.encoder).noSpaces
}

object  ResponseErrorBody {

  implicit object encoder extends Encoder[ResponseErrorBody] {
    override def apply(a: ResponseErrorBody): Json = {
          Json.obj(
            ("num", Json.fromInt(a.num)),
            ("message", Json.fromString(a.message)),
          )
      }
  }

  // this is not used
//  implicit object decoder extends Decoder[ResponseErrorBody] {
//    override def apply(c: HCursor): Result[ResponseErrorBody] = {
//      for {
//        login <- c.downField("login").as[String]
//        pass <- c.downField("pass").as[String]
//        userId <- c.downField("userId").as[String]
//      } yield {
//        if (userId.isEmpty) LoginCredentials(login, pass, None)
//        else LoginCredentials(login, pass, Option(UUID.fromString(userId)))
//      }
//    }
//  }
}
