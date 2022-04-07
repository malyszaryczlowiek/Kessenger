package com.github.malyszaryczlowiek
package db

import java.util.UUID
import scala.collection.mutable.ListBuffer
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.domain.User

/**
 * In memory DB
 */
object DB {
  val users = List(
    User(UUID.randomUUID(), "User1", "Name1"),
    User(UUID.randomUUID(), "User2", "Name2")
  )

  val talks: ListBuffer[(ChatId, ChatName, List[UUID])] = new ListBuffer[(ChatId, ChatName, List[UUID])]


  def searchUser(secName:String): Option[User] =
    users.find(_.secondName == secName)


  def getUsersTalks(user: User): List[(ChatId, ChatName)] =
    talks.filter(_._3.contains(user.userId)).map(talk => (talk._1, talk._2)).toList
    TODO
    1. wyciągnij teraz dane topiki
    2. z topików wyciągnij timestampy
    3. przypisz timestampy do talka
    4. uszereguj talka względem od najnowszego do najstarszego.

}
