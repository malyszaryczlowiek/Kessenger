package com.github.malyszaryczlowiek
package db.queries

import domain.Domain.{ChatId, ChatName, Login, Password}


trait Creatable extends DbStatements :
  def createUser(login: Login, pass: Password): Query
  def createChat(chatId: ChatId, chatName: ChatName): Query
