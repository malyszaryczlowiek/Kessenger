package com.github.malyszaryczlowiek
package account

import messages.Chat

import com.github.malyszaryczlowiek.domain.Domain.{UserID, Login}

object MyAccount:

  private var myChats: List[Chat] = _
  private var myUUID: UserID = _
  private var myLogin: Login = _


  def initialize(): Unit = ???
  def openChat(i: Int): Unit = ???