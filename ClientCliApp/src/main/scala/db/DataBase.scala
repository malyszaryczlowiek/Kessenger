package com.github.malyszaryczlowiek
package db

import domain.User

import com.github.malyszaryczlowiek.messages.Chat

trait DataBase :
  def connectToDB(): Unit
  def createUser(newUser: User): User // returned new user
  def searchUser(name: String): Option[User]
  def searchUsersChats(user: User): Option[List[Chat]]
