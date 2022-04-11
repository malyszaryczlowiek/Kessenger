package com.github.malyszaryczlowiek
package db

import domain.User

import com.github.malyszaryczlowiek.db.queries.QueryError
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, Interlocutor, Sender}
import com.github.malyszaryczlowiek.domain.PasswordConverter.Password
import com.github.malyszaryczlowiek.messages.Chat

import java.util.UUID
import scala.util.Try

trait DataBase:

  def createUser(login: String, pass: Password): Try[Either[QueryError, User]]
  def createChat(chatId: ChatId, chatName: ChatName): Try[Either[QueryError, Chat]]

  def readUsersChats(user: User, pass: String): Try[Either[QueryError, Seq[Chat]]]
  def findUser(user: User): Try[Either[QueryError, User]]
  def findUser(login: String): Try[Either[QueryError, User]]

  def updateUsersPassword(user: User, pass: String): Try[Either[QueryError, Boolean]]
  def updateChatName(chatId: ChatId, newName: String): Try[Either[QueryError, String]]
  def updateUsersChat(userId: UUID, chatId: ChatId): Try[Either[QueryError, Boolean]]

  def deleteUser(user: User): Try[Either[QueryError, User]]
  def deleteUser(userId: UUID): Try[Either[QueryError, User]]
  def deleteChat(chatId: ChatId): Try[Either[QueryError, User]]

  def closeConnection(): Try[Unit]
