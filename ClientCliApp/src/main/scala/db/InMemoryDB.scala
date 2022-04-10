package com.github.malyszaryczlowiek
package db

import java.util.UUID
import scala.collection.mutable.ListBuffer
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.domain.User

/**
 * In memory DB
 */
object InMemoryDB extends DataBase {
  val users = List(
    User(UUID.fromString("9039d5ad-99d5-47af-a9ae-a8afee0bf2e8"), "User1", "Name1"), //
    User(UUID.fromString("197cc871-2da6-4f76-9ead-6c45f0020768"), "User2", "Name2")  //
  )

  val talks: ListBuffer[(ChatId, ChatName, List[UUID])] = new ListBuffer[(ChatId, ChatName, List[UUID])]


  def searchUser(secName:String): Option[User] =
    users.find(_.secondName == secName)


  def getUsersChats(user: User): Vector[(Int, ChatId, ChatName)] =
    talks
      .filter( _._3.contains(user.userId) )
      .map(talk => (talk._1, talk._2))
      .zipWithIndex
      .map((t, index) => (index, t._1, t._2))
      .toVector


  //    TODO
//    1. wyciągnij teraz dane topiki
//    2. z topików wyciągnij timestampy
//    3. przypisz timestampy do talka
//    4. uszereguj talka względem od najnowszego do najstarszego.

}
