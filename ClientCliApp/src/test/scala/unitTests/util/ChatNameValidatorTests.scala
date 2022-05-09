package com.github.malyszaryczlowiek
package unitTests.util

//import com.github.malyszaryczlowiek.util.ChatNameValidator.isValid
import util.ChatNameValidator.isValid

class ChatNameValidatorTests extends munit.FunSuite :

  test("single char should pass") {
    val name = "s"
    assert(isValid(name))
  }

  test("single whitespace should fail") {
    val name = " "
    assert( ! isValid(name) )
  }

  test("empty string should fail") {
    val name = ""
    assert( ! isValid(name) )
  }

  test("char with whitespace and number should pass.") {
    val name = "r 3"
    assert(isValid(name))
  }

  test("char with whitespace and number should pass.") {
    val name = "Tatry 2022"
    assert(isValid(name))
  }