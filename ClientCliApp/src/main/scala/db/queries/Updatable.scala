package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Password, UserID}

trait Updatable extends DbStatements:
  def updateUsersPassword(user: User, pass: Password): Query
  def updateChatName(chatId: ChatId, newName: ChatName): Query
  def updateUsersChat(userId: UserID, chatId: ChatId): Query

