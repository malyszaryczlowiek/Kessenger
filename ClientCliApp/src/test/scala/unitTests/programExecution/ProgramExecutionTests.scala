package com.github.malyszaryczlowiek
package unitTests.programExecution

import java.time.LocalDateTime
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

  test("is empty string really empty") {
    assert("".isEmpty)
  }

  test("Equality test") {
    val e = "e"
    assert("e".equals(e))
  }


  // todo wstawic to do Program Executor'a
  test("Does login regex match example incorrect logins.") {
    val loginRegex = "[\\p{Punct}a-z0-9]+|([0-9]+)".r

    // these logins should NOT be valid
    assert(   loginRegex.matches("#")     )
    assert(   loginRegex.matches("#exi")  )
    assert(   loginRegex.matches("e$xi")  )
    assert(   loginRegex.matches("&99")   )
    assert(   loginRegex.matches("499")   )

    // these logins should be valid
    assert( ! loginRegex.matches("Walo")  )
    assert( ! loginRegex.matches("ę")     )
    assert( ! loginRegex.matches("ę99")   )


  }

//  test("testing inserting string to string") {
//    val chat = Chat("", "Foo chat name", false, 0L, LocalDateTime.now())
//    val forGrouped = " or #escape_chat"
//    println(s"you are in ${chat.chatName}, type '#back' to return to chat list${if chat.groupChat then $forGrouped}.")
//  }