package com.github.malyszaryczlowiek
package db.queries

import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName}
import com.github.malyszaryczlowiek.domain.PasswordConverter.Password

import java.util.UUID

object PostgresStatements extends Queryable:

  // from Creatable
  def createUser(login: String, pass: Password): Query =
    s"INSERT INTO users VALUES ( gen_random_uuid (), '${login}', '${pass}')"
  def createChat(chatId: ChatId, chatName: ChatName): Query =
    s"INSERT INTO chats(chat_id, chat_name) VALUES ('$chatId', '$chatName')"


  // from Readable
  def readUsersChats(user: User, pass: String): Query = ???
  def findUser(user: User): Query = ???
  def findUser(login: String): Query = ???

  // from Updatable
  def updateUsersPassword(user: User, pass: String): Query = ???
  def updateChatName(chatId: ChatId, newName: String): Query = ???
  def updateUsersChat(userId: UUID, chatId: ChatId): Query = ???


  // from Deletable
  def deleteUser(user: User): Query = ???
  def deleteUser(userId: UUID): Query = ???
  def deleteChat(chatId: ChatId): Query = ???
