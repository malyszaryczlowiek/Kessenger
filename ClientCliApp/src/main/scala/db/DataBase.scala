package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.util.UUID
import scala.util.Try

trait DataBase:
  // type QueryResult[A] = Either[QueryError, A]

  def createUser(login: Login, pass: Password): Either[QueryError, User]
  def createChat(users: List[User], chatName: ChatName): Either[QueryError, Chat]

  def findUsersChats(user: User): Either[QueryError,Seq[Chat]]
  def findUsersChats(userId: UserID): Either[QueryError,Seq[Chat]]
  def findUsersChats(login: Login): Either[QueryError,Seq[Chat]]
  def findUser(user: User): Either[QueryError, User]
  def findUser(login: Login): Either[QueryError,User]
  def findUser(userId: UserID): Either[QueryError,User]

  def updateUsersPassword(user: User, pass: Password): Either[QueryError,User]
  def updateChatName(chatId: ChatId, newName: ChatName): Either[QueryError,ChatName]
  def addUsersToChat(userIds: List[UserID], chatId: ChatId): List[Either[QueryError,UserID]]  // add user to chat

  def deleteUserPermanently(user: User): Either[QueryError,User]
  def deleteUserPermanently(userId: UserID): Either[QueryError,User]
  def deleteUserFromChat(chatId: ChatId, userID: UserID): Either[QueryError,User]
  def deleteChat(chatId: ChatId): Either[QueryError,Chat]