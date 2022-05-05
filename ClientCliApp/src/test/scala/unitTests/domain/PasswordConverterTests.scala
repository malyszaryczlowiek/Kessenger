package com.github.malyszaryczlowiek
package unitTests.domain

import com.github.malyszaryczlowiek.util.PasswordConverter
import com.github.malyszaryczlowiek.domain.Domain.Password


import scala.util.{Failure, Success}

class PasswordConverterTests extends munit.FunSuite :

  test("testing correctness of hashing and dehashing") {
    val pass = "password"
    val salt = "$2a$10$8K1p/a0dL1LXMIgoEDFrwO"
    val t = PasswordConverter.convert(pass, salt)
    t match {
      case Left(value) => assert(false, value)
      case Right(hash) =>
        PasswordConverter.validatePassword(pass, hash) match {
          case Failure(exception) => assert(false, exception.getMessage)
          case Success(value) => assert(value)
        }
    }
  }

  test ("generate password") {
    val salt = "$2a$10$8K1p/a0dL1LXMIgoEDFrwO"
    PasswordConverter.convert("bbb", salt) match {
      case Right(p: Password) => println(p)
      case Left(e) => println(e)
    }
  }
