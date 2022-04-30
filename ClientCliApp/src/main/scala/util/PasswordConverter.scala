package com.github.malyszaryczlowiek
package util

import domain.Domain.Password
import com.github.t3hnar.bcrypt.*

import scala.util.{Failure, Success, Try}

object PasswordConverter:

  def convert(pas: => String): Either[String, Password] =
//    val notAcceptable = SecurityValidator.isValid(pas)
//    if notAcceptable.isEmpty then
    pas.bcryptSafeBounded match
      case Failure(exception) => Left(exception.getMessage)
      case Success(encrypted) => Right(encrypted)
//    else
//      val forbidden =
//        notAcceptable.foldLeft[String]("")((str: String, c: Char) => s"$str, $c")
//          .substring(1)
//      Left(s"Password contains forbidden characters:$forbidden")


  def validatePassword(pass: String, hash: Password): Try[Boolean] =
    pass.isBcryptedSafeBounded(hash)
