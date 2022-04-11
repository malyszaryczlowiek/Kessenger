package com.github.malyszaryczlowiek
package db.queries

import domain.User
import com.github.malyszaryczlowiek.domain.Domain.ChatId

trait Updatable extends DbStatements:
  def updateUsersPassword(user: User, pass: String): Query
  def updateChatName(chatId: ChatId, newName: String): Query

