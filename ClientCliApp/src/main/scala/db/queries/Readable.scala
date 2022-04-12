package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}

trait Readable extends DbStatements:
  def findUsersChats(user: User): Query
  def findUsersChats(userId: UserID): Query
  def findUsersChats(login: Login): Query
  def findUser(login: Login): Query
  def findUser(userId: UserID): Query
// login is not changeable.