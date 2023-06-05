package util

import components.model.LoginCredentials
import io.circe.Decoder.Result
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Error, HCursor, Json}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{Offset, Partition}
import io.github.malyszaryczlowiek.kessengerlibrary.model._

import java.util.UUID



class JsonParsers {


  private implicit object userAndMessageDecoder extends Decoder[(User, Message)] {
    override def apply(c: HCursor): Result[(User, Message)] = {
      for {
        user    <- c.downField("user").as[User]
        message <- c.downField("message").as[Message]
      } yield {
        (user, message)
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


  private implicit object newChatUsersJSONDecoder extends Decoder[(String, String, List[UUID], List[PartitionOffset])] {
    override def apply(c: HCursor): Result[(String, String, List[UUID], List[PartitionOffset])] = {
      for {
        login    <- c.downField("invitersLogin").as[String]
        chatName <- c.downField("chatName").as[String]
        users    <- c.downField("users").as[List[String]]
        partitionOffsets <- c.downField("partitionOffsets").as[List[PartitionOffset]]
      } yield {
        (login, chatName, users.map(UUID.fromString), partitionOffsets)
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


  private implicit object chatEncoder extends Encoder[(Chat, Map[Partition, Offset])] {
    override def apply(a: (Chat, Map[Partition, Offset])): Json = {
      Json.obj(
        ("chat", a._1.asJson),
        ("partitionOffsets", a._2.map(tup => {
          Json.obj(
            ("partition", Json.fromInt(tup._1)),
            ("offset", Json.fromLong(tup._2))
          )
        }).asJson) // ,
        //          ("users", List.empty[User].asJson),
        //          ("messages", List.empty[Message].asJson)
      )
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


  private implicit object userSettingsEncoder extends Encoder[(User, Settings)] {
    override def apply(a: (User, Settings)): Json =
      Json.obj(
        ("user", a._1.asJson),
        ("settings", a._2.asJson)
      )
  }

  private implicit object userDataEncoder extends Encoder[(User, Settings, List[(Chat, List[PartitionOffset])])] {
    override def apply(a: (User, Settings, List[(Chat, List[PartitionOffset])])): Json =
      Json.obj(
        ("user", a._1.asJson),
        ("settings", a._2.asJson),
        ("chatList", a._3.map(t => Json.obj(
          ("chat", t._1.asJson),
          ("partitionOffsets", t._2.map(_.asJson).asJson)
        )).asJson)
      )
  }


  def toJSON(u: (User, Settings, List[(Chat, List[PartitionOffset])])): String = u.asJson.noSpaces
  def toJSON(u: (User, Settings)): String = u.asJson.noSpaces

  def chatToJSON(c: (Chat, Map[Partition, Offset])): String = c.asJson(chatEncoder).noSpaces

  def chatsToJSON(map: Map[Chat, Map[Partition, Offset]]): String = map.asJson.noSpaces

  def parseNewChat(json: String): Either[Error,(User, List[UUID], String)] = decode[(User, List[UUID], String)](json)(newChatJSONDecoder)

  def parseCredentials(json: String): Either[Error, LoginCredentials] = decode[LoginCredentials](json)
  def parseNewChatUsers(json: String): Either[Error, (String, String, List[UUID], List[PartitionOffset])] =
    decode[(String, String, List[UUID], List[PartitionOffset])](json)(newChatUsersJSONDecoder)
  def parseNewPass(json: String): Either[Error, (String, String)] = decode[(String, String)](json)(newPassDecoder)

  def parseUserAndMessage(json: String): Either[Error, (User, Message)] = decode[(User, Message)](json)(userAndMessageDecoder)


}



