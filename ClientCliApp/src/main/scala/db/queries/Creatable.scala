package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}

trait Creatable extends DbStatements :
  def createUser(user: User): Query
  def createChat(chatId: ChatId, chatName: ChatName): Query
