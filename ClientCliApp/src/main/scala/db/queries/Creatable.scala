package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Password, Login}


trait Creatable extends DbStatements :
  def createUser(login: Login, pass: Password): Query
  def createChat(chatId: ChatId, chatName: ChatName): Query
