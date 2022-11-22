package components.util.converters

import components.util.LoginCredentials.LoginCredentials
import io.github.malyszaryczlowiek.kessengerlibrary.util.TimeConverter
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, Error, HCursor, Json}
import io.circe.syntax._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.{Chat, User}
import io.circe.parser.decode
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{Offset, Partition}

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



  private implicit object newGroupChatJSONDecoder extends Decoder[(List[User], String)] {
    override def apply(c: HCursor): Result[(List[User], String)] = {
      for {
        users    <- c.downField("users").as[List[User]]
        chatName <- c.downField("chatName").as[String]
      } yield {
        (users, chatName)
      }
    }
  }


  private implicit object newChatJSONDecoder extends Decoder[(User, UUID, String)] {
    override def apply(c: HCursor): Result[(User, UUID, String)] = {
      for {
        user     <- c.downField("user").as[User]
        other_id <- c.downField("otherId").as[String]
        chatName <- c.downField("chatName").as[String]
      } yield {
        (user, UUID.fromString(other_id), chatName)
      }
    }
  }

  private implicit object newChatUsersJSONDecoder extends Decoder[(List[UUID], String, String)] {
    override def apply(c: HCursor): Result[(List[UUID], String, String)] = {
      for {
        users    <- c.downField("users").as[List[String]]
        chatId   <- c.downField("chatId").as[String]
        chatName <- c.downField("chatName").as[String]
      } yield {
        (users.map(UUID.fromString), chatId, chatName)
      }
    }
  }


  // ()
  private implicit object usersChatsEncoder extends Encoder[Map[Chat, Map[Partition, Offset]]] {
    override def apply(a: Map[Chat, Map[Partition, Offset]]): Json = {
      a.map(keyValue => {
        (keyValue._1.chatId, keyValue._1.chatName, keyValue._1.groupChat, keyValue._1.lastMessageTime, keyValue._2.map(kv => (kv._1, kv._2)).toList)
      }).map( tupple => {
        Json.obj(
          ("chatId", Json.fromString(tupple._1)),
          ("chatName", Json.fromString(tupple._2)),
          ("groupChat", Json.fromBoolean(tupple._3)),
          ("lastMessageTime", Json.fromLong( tupple._4)),
          ("partitionOffsets", tupple._5.map( tup => {
            Json.obj(
              ("partition", Json.fromInt(tup._1)),
              ("offset", Json.fromLong(tup._2))
            )
          }).asJson)
        )
      })
    }.asJson
  }




  def toJson(l: List[String]): String = l.asJson.noSpaces
  def toJSON(us: Iterable[User]): String = us.asJson.noSpaces



  def toJSON(u: User): String = u.asJson.noSpaces
  def chatsToJSON(map: Map[Chat, Map[Partition, Offset]]): String = map.asJson.noSpaces



  def toListOfUsers(s: String): Either[Error,List[User]] = decode[List[User]](s)
  def toUser(json: String): Either[Error, User] = decode[User](json)
  def newChatJSON(json: String): Either[Error, (User, UUID, String)] = decode[(User, UUID, String)](json)
  def newGroupChatJSON(json: String): Either[Error,(List[User], String)] = decode[(List[User], String)](json)
  def parseLoginAndPass(json: String): Either[Error,LoginCredentials] = decode[LoginCredentials](json)
  def parsNewChatUsers(json: String): Either[Error,(List[UUID], String, String)] = decode[(List[UUID], String, String)](json)


}
