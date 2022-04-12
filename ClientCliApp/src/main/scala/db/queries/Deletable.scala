package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.db.queries.PostgresStatements.Query
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, UserID}

import java.util.UUID

trait Deletable extends DbStatements :
  def deleteUserPermanently(user: User): Query
  def deleteUserPermanently(userId: UserID): Query
  def deleteUserFromChat(chatId: ChatId, userId: UserID): Query
  def deleteChat(chatId: ChatId): Query
