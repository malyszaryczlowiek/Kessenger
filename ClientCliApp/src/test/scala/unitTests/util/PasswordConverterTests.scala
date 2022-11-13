package io.github.malyszaryczlowiek
package unitTests.util

import util.PasswordConverter

class PasswordConverterTests extends munit.FunSuite {

  private val defaultSalt = "$2a$10$8K1p/a0dL1LXMIgoEDFrwO"

  test("generating new passwords") {
    PasswordConverter.convert("Password1!", defaultSalt) match
      case Left(value) => ???
      case Right(converted) => println(converted)

    PasswordConverter.convert("Password2!", defaultSalt) match
      case Left(value) => ???
      case Right(converted) => println(converted)

    println( System.currentTimeMillis() / 1000L )

  }

}
