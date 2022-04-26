package com.github.malyszaryczlowiek
package account

import messages.Chat

import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.User

import java.util.UUID

object MyAccount:

  private var me: User = _// User(UUID.randomUUID(), "")

  private var myChats: Map[Chat, List[User]] = _
  private var myUUID: UserID = _
  private var myLogin: Login = _


  def initialize(): Unit = ???
  def openChat(i: Int): Unit = ???

  def getMyObject: User = me