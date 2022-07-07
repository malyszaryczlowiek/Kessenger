package com.github.malyszaryczlowiek
package util


import kessengerlibrary.domain.Domain.Password

import com.github.t3hnar.bcrypt
import com.github.t3hnar.bcrypt.*

import scala.util.{Failure, Success, Try}


object PasswordConverter:

  def generateSalt: String = bcrypt.generateSalt

  def convert(pas: => String, salt: => String): Either[String, Password] =
    pas.bcryptSafeBounded(salt) match
      case Failure(exception) => Left(exception.getMessage)
      case Success(encrypted) => Right(encrypted)
  

  def validatePassword(pass: String, hash: Password): Try[Boolean] =
    pass.isBcryptedSafeBounded(hash)
