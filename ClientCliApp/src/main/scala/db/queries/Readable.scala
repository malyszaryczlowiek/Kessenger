package com.github.malyszaryczlowiek
package db.queries

import domain.User

trait Readable extends DbStatements:
  def readUsersChats(user: User, pass: String): Query
  def findUser(user: User): Query
  def findUser(login: String): Query

  // login is not changeable.
