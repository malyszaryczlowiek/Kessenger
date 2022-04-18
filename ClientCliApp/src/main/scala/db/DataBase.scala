package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.db.queries.QueryErrors
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Login, Password, UserID}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.Chat

import java.util.UUID
import scala.util.Try

trait DataBase:
  // type QueryResult[A] = Either[QueryErrors, A]

  def createUser(login: Login, pass: Password): Either[QueryErrors, User]
  def createChat(users: List[User], chatName: ChatName): Either[QueryErrors, Chat]

  def findUsersChats(user: User): Either[QueryErrors,Seq[Chat]]
  def findUsersChats(userId: UserID): Either[QueryErrors,Seq[Chat]]
  def findUsersChats(login: Login): Either[QueryErrors,Seq[Chat]]
  def findUser(user: User): Either[QueryErrors, User]
  def findUser(login: Login): Either[QueryErrors,User]
  // def findUser(userId: UserID): Either[QueryErrors,User]

  def updateUsersPassword(user: User, pass: Password): Either[QueryErrors,User]
  def updateChatName(chatId: ChatId, newName: ChatName): Either[QueryErrors,ChatName]
  def addNewUsersToChat(userIds: List[UserID], chatId: ChatId): List[Either[QueryErrors,UserID]]  // add user to chat

  def deleteUserPermanently(user: User): Either[QueryErrors,User]
  def deleteUserPermanently(userId: UserID): Either[QueryErrors,User]
  def deleteUsersFromChat(chatId: ChatId, userID: UserID): Either[QueryErrors,User]
  def deleteChat(chatId: ChatId): Either[QueryErrors,Chat]