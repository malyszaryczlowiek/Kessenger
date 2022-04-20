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
  def findUser(user: User): Either[QueryErrors, User]
  def findUser(login: Login): Either[QueryErrors, User]

  def updateUsersPassword(user: User, oldPass: Password, newPass: Password): Either[QueryErrors,User]
  def updateMyLogin(me: User, newLogin: Login, pass: Password): Either[QueryErrors,User]
  def updateChatName(chat: Chat, newName: ChatName): Either[QueryErrors,ChatName]
  def addNewUsersToChat(userIds: List[User], chat: Chat): Either[QueryErrors,Chat]

  def deleteMeFromChat(me: User, chat: Chat): Either[QueryErrors, Chat]
  def deleteMyAccountPermanently(user: User, pass: Password): Either[QueryErrors,User]