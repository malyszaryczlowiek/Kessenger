package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.util.UUID
import scala.util.Try

trait DataBase:
  type QueryResult[A] = Either[QueryError, A]

  def createUser(login: Login, pass: Password): QueryResult[User]
  def createChat(chatId: ChatId, chatName: ChatName): QueryResult[Chat]

  def findUsersChats(user: User): QueryResult[Seq[Chat]]
  def findUsersChats(userId: UserID): QueryResult[Seq[Chat]]
  def findUsersChats(login: Login): QueryResult[Seq[Chat]]
  def findUser(login: Login): QueryResult[User]
  def findUser(userId: UserID): QueryResult[User]

  def updateUsersPassword(user: User, pass: Password): QueryResult[User]
  def updateChatName(chatId: ChatId, newName: ChatName): QueryResult[ChatName]
  def addUsersToChat(userIds: List[UserID], chatId: ChatId): List[QueryResult[UserID]]  // add user to chat

  def deleteUserPermanently(user: User): QueryResult[User]
  def deleteUserPermanently(userId: UserID): QueryResult[User]
  def deleteUserFromChat(chatId: ChatId, userID: UserID): QueryResult[User]
  def deleteChat(chatId: ChatId): QueryResult[Chat]