package components.util.converters

import components.util.LoginCredentials.LoginCredentials
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, Error, HCursor, Json}
import io.circe.syntax._
import io.circe.parser.decode
import io.github.malyszaryczlowiek.kessengerlibrary.domain.{Chat, Settings, User}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{Offset, Partition}
import io.github.malyszaryczlowiek.kessengerlibrary.messages.Message
import models.ResponseErrorBody

import java.time.ZoneId
import java.util.UUID
import scala.collection.mutable.ListBuffer


class JsonParsers {

  private implicit object listUserEncoder extends Encoder[Iterable[User]]  {
    override def apply(a: Iterable[User]): Json = a.map(u => u.asJson).asJson
  }


  private implicit object listUserDecoder extends Decoder[List[User]] {
    override def apply(c: HCursor): Result[List[User]] = {
      c.values match {
        case Some(i) =>
          val buffer = ListBuffer.empty[User]
          val parsed = i.map((u: Json) => {
            val uc = u.hcursor
            for {
              userId <- uc.downField("userId").as[String]
              login  <- uc.downField("login") .as[String]
            } yield {
              User(UUID.fromString(userId), login)
            }
          })
          for (p <- parsed) {
            p match {
              case Left(_) =>
              case Right(user) => buffer.addOne(user)
            }
          }
          Right(buffer.toList)
        case None => Right(List.empty[User])
      }
    }
  }



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


  private implicit object settingsDecoder extends Decoder[Settings] {
    override def apply(c: HCursor): Result[Settings] = {
      for {
        joiningOffset   <- c.downField("joiningOffset")  .as[Long]
        sessionDuration <- c.downField("sessionDuration").as[Long]
        zoneId          <- c.downField("zoneId")         .as[String]
      } yield {
        Settings(joiningOffset , sessionDuration , ZoneId.of(zoneId) )
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
            )}).asJson),
          ("users", List.empty[User].asJson),
          ("messages", List.empty[Message].asJson)
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



  def toJson(l: List[String]): String = l.asJson.noSpaces
  def toJSON(us: Iterable[User]): String = us.asJson.noSpaces
  def toJSON(u: (User, Settings)): String = u.asJson.noSpaces
  def toJSON(u: User): String = u.asJson.noSpaces
  def toJSON(e: ResponseErrorBody): String = e.asJson.noSpaces

  def chatsToJSON(map: Map[Chat, Map[Partition, Offset]]): String = map.asJson.noSpaces



  def parseListOfUsers(json: String): Either[Error, List[User]] = decode[List[User]](json)
  def parseUser(json: String): Either[Error, User] = decode[User](json)
//   def newChatJSON(json: String): Either[Error, (User, UUID, String)] = decode[(User, UUID, String)](json)
  def parseNewChat(json: String): Either[Error,(User, List[UUID], String)] = decode[(User, List[UUID], String)](json)(newChatJSONDecoder)
  def parseCredentials(json: String): Either[Error, LoginCredentials] = decode[LoginCredentials](json)
  def parseNewChatUsers(json: String): Either[Error, (String, List[UUID])] = decode[(String, List[UUID])](json)(newChatUsersJSONDecoder)

  def parseSettings(json: String):  Either[Error, Settings] = decode[Settings](json)(settingsDecoder)

  def parseNewPass(json: String): Either[Error, (String, String)] = decode[(String, String)](json)(newPassDecoder)


}








//  @deprecated
//  private  object usersChatsEncoder2 extends Encoder[Map[Chat, Map[Partition, Offset]]] {
//    override def apply(a: Map[Chat, Map[Partition, Offset]]): Json = {
//      a.map(keyValue => {
//        (keyValue._1.chatId, keyValue._1.chatName, keyValue._1.groupChat, keyValue._1.lastMessageTime, keyValue._1.silent, keyValue._2.map(kv => (kv._1, kv._2)).toList)
//      }).map( tupple => {
//        Json.obj(
//          ("chatId", Json.fromString(tupple._1)),
//          ("chatName", Json.fromString(tupple._2)),
//          ("groupChat", Json.fromBoolean(tupple._3)),
//          ("lastMessageTime", Json.fromLong( tupple._4)),
//          ("lastMessageTime", Json.fromBoolean( tupple._5)),
//          ("partitionOffsets", tupple._6.map( tup => {
//            Json.obj(
//              ("partition", Json.fromInt(tup._1)),
//              ("offset", Json.fromLong(tup._2))
//            )
//          }).asJson)
//        )
//      })
//    }.asJson
//  }

//
//  @deprecated
//  private implicit object newChatJSONDecoder4 extends Decoder[(User, UUID, String)] {
//    override def apply(c: HCursor): Result[(User, UUID, String)] = {
//      for {
//        user     <- c.downField("user").as[User]
//        other_id <- c.downField("otherId").as[String]
//        chatName <- c.downField("chatName").as[String]
//      } yield {
//        (user, UUID.fromString(other_id), chatName)
//      }
//    }
//  }
//
//
//  @deprecated
//  private object newChatUsersJSONDecoder2 extends Decoder[(List[UUID], String, String)] {
//    override def apply(c: HCursor): Result[(List[UUID], String, String)] = {
//      for {
//        users    <- c.downField("users").as[List[String]]
//        chatId   <- c.downField("chatId").as[String]
//        chatName <- c.downField("chatName").as[String]
//      } yield {
//        (users.map(UUID.fromString), chatId, chatName)
//      }
//    }
//  }
