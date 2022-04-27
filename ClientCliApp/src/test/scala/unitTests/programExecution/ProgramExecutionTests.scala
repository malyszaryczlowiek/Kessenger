package com.github.malyszaryczlowiek
package unitTests.programExecution

import scala.io.StdIn.readLine

class ProgramExecutionTests extends munit.FunSuite:

  test("password regex test") {

    val login = "abba33"
    val regex = "[\\w]{7,}".r
    assert( regex.matches(login) )

  }