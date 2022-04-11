package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.ChatId

import java.util.UUID

trait Deletable extends DbStatements :
  def deleteUser(user: User): Query
  def deleteUser(userId: UUID): Query
  def deleteChat(chatId: ChatId): Query
