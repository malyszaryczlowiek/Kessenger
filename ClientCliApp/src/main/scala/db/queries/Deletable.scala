package com.github.malyszaryczlowiek
package db.queries

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, UserID}
import com.github.malyszaryczlowiek.domain.User

import java.util.UUID

trait Deletable extends DbStatements :
  def deleteUserPermanently(user: User): Query
  def deleteUserPermanently(userId: UserID): Query
  def deleteUserFromChat(chatId: ChatId, userId: UserID): Query
  def deleteChat(chatId: ChatId): Query
