package com.github.malyszaryczlowiek
package unitTests.domain

import com.github.malyszaryczlowiek.util.PasswordConverter

import scala.util.{Failure, Success}

class PasswordConverterTests extends munit.FunSuite :

  test("testing correctness of hashing and dehashing") {
    val pass = "password"
    val t = PasswordConverter.convert(pass)
    t match {
      case Left(value) => assert(false, value)
      case Right(hash) =>
        PasswordConverter.validatePassword(pass, hash) match {
          case Failure(exception) => assert(false, exception.getMessage)
          case Success(value) => assert(value)
        }
    }
  }
