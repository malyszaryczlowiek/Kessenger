package com.github.malyszaryczlowiek
package unitTests.programExecution

import scala.io.StdIn.readLine

class ProgramExecutionTests extends munit.FunSuite:

  test("password regex test".fail) {
    val login = "abba33"
    val regex = "[\\w]{8,}".r
    assert( regex.matches(login) )
  }

  test("password regex test 2") {
    val login = "abba3366"
    val regex = "[\\w]{8,}".r
    assert( regex.matches(login) )
  }

  test("testing Array[Char] to string conversion") {
    val array = Array[Char]('d', 'd', 'e')
    assert(array.mkString("") == "dde")
  }