package components.util.converters

import components.util.LoginCredentials.LoginCredentials

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, Error, HCursor, Json}
import io.circe.syntax._
import io.circe.parser.decode

import io.github.malyszaryczlowiek.kessengerlibrary.model.{Chat, Settings, User}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{Offset, Partition}


import java.util.UUID



class JsonParsers {


  private implicit object newChatJSONDecoder extends Decoder[(User, List[UUID], String)] {
    override def apply(c: HCursor): Result[(User, List[UUID], String)] = {
      for {
        me       <- c.downField("me").as[User]
        users    <- c.downField("users").as[List[String]]
        chatName <- c.downField("chatName").as[String]
      } yield {
        (me, users.map(UUID.fromString), chatName)
      }
    }
  }


  private implicit object newChatUsersJSONDecoder extends Decoder[(String, List[UUID])] {
    override def apply(c: HCursor): Result[(String, List[UUID])] = {
      for {
        chatName <- c.downField("chatName").as[String]
        users    <- c.downField("users").as[List[String]]
      } yield {
        (chatName, users.map(UUID.fromString))
      }
    }
  }



  private implicit object newPassDecoder extends Decoder[(String, String)] {
    override def apply(c: HCursor): Result[(String, String)] = {
      for {
        oldP <- c.downField("oldPass").as[String]
        newP <- c.downField("newPass").as[String]
      } yield {
        (oldP, newP)
      }
    }
  }


  private implicit object usersChatsEncoder extends Encoder[Map[Chat, Map[Partition, Offset]]] {
    override def apply(a: Map[Chat, Map[Partition, Offset]]): Json = {
      a.map(keyValue => {
        Json.obj(
          ("chat", keyValue._1.asJson),
          ("partitionOffsets", keyValue._2.map(tup => {
            Json.obj(
              ("partition", Json.fromInt(tup._1)),
              ("offset", Json.fromLong(tup._2))
            )}).asJson) // ,
//          ("users", List.empty[User].asJson),
//          ("messages", List.empty[Message].asJson)
        )
      }).asJson
    }
  }


  private implicit object userSettingsEncoder extends Encoder[(User,Settings)] {
    override def apply(a: (User, Settings)): Json =
      Json.obj(
        ("user", a._1.asJson),
        ("settings", a._2.asJson)
      )
  }


  def toJSON(u: (User, Settings)): String = u.asJson.noSpaces

  def chatsToJSON(map: Map[Chat, Map[Partition, Offset]]): String = map.asJson.noSpaces

  def parseNewChat(json: String): Either[Error,(User, List[UUID], String)] = decode[(User, List[UUID], String)](json)(newChatJSONDecoder)

  def parseCredentials(json: String): Either[Error, LoginCredentials] = decode[LoginCredentials](json)
  def parseNewChatUsers(json: String): Either[Error, (String, List[UUID])] = decode[(String, List[UUID])](json)(newChatUsersJSONDecoder)
  def parseNewPass(json: String): Either[Error, (String, String)] = decode[(String, String)](json)(newPassDecoder)


}



