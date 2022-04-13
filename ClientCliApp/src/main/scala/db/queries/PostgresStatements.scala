package com.github.malyszaryczlowiek
package db.queries


import com.github.malyszaryczlowiek.domain.Domain.{UserID, ChatId, ChatName, Login, Password}
import com.github.malyszaryczlowiek.domain.User

import java.util.UUID

object PostgresStatements extends Queryable:

  // from Creatable
  def createUser(login: Login, pass: Password): Query =
    s"INSERT INTO users(login,pass) VALUES ( '${login}', '${pass}')"
  def createChat(chatId: ChatId, chatName: ChatName): Query =
    s"INSERT INTO chats(chat_id, chat_name) VALUES ('$chatId', '$chatName')"


  // from Readable
  def findUsersChats(user: User): Query =
    s"SELECT users_chats.chat_id, chats.chat_name FROM users_chats INNER JOIN chats WHERE users_chats.user_id = chats.chat_id AND users_chats.user_id = ${user.userId}"
  def findUsersChats(userId: UserID): Query =
    s"SELECT users_chats.chat_id, chats.chat_name FROM users_chats INNER JOIN chats WHERE users_chats.user_id = chats.chat_id AND users_chats.user_id = ${userId}"
  def findUsersChats(login: Login): Query =
    s"SELECT users_chats.chat_id, chats.chat_name FROM users_chats INNER JOIN chats WHERE users_chats.user_id = chats.chat_id AND users.login = ${login}"
  def findUser(login: Login): Query =
    s"SELECT * FROM users WHERE login=$login"
  def findUser(userId: UserID): Query =
    s"SELECT * FROM users WHERE user_id=$userId"


  // from Updatable
  def updateUsersPassword(user: User, pass: Password): Query =
    s"UPDATE users SET pass = $pass WHERE users.user_id = ${user.userId} AND users.login = ${user.login}"
  def updateChatName(chatId: ChatId, newName: ChatName): Query =
    s"UPDATE chats SET chat_name = $newName WHERE chats.chat_id = ${chatId}"
  def updateUsersChat(userId: UserID, chatId: ChatId): Query =
    s"INSERT INTO users_chats( chat_id, user_id ) VALUES ( $chatId, $userId )"


  // from Deletable
  def deleteUserPermanently(user: User): Query =
    s"DELETE FROM users WHERE user_id = ${user.userId} AND login = ${user.login} RETURNING *"
  def deleteUserPermanently(userId: UserID): Query =
    s"DELETE FROM users WHERE user_id = ${userId} RETURNING *"
  def deleteUserFromChat(chatId: ChatId, userId: UserID): Query =
    s"DELETE FROM users_chats WHERE chat_id = $chatId AND user_id = ${userId} RETURNING *"
  def deleteChat(chatId: ChatId): Query =
    s"DELETE FROM chats WHERE chat_id = $chatId"

// TODO tutaj należy teraz zaimplementować wszystkie
//   i popisac trochę testów sprawdzających poprawność
//   napisać test czy rege w Security Validator działa poprawnie.