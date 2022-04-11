package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.domain.PasswordConverter.Password

trait Creatable extends DbStatements :
  def createUser(login: String, pass: Password): Query
  def createChat(chatId: ChatId, chatName: ChatName): Query
