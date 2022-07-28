package com.github.malyszaryczlowiek
package unitTests.messages

import kessengerlibrary.domain.{Chat, User}

import messages.MessagePrinter
import messages.MessagePrinter.messagePrinterReverseOrdering  // given

import java.time.{LocalDateTime, Month}
import java.util.UUID

class MessagePrinterTests extends munit.FunSuite:


  test("Testing ordering of MessagePrinter") {

    val chat1 = Chat("fakeID", "fakeName",false, 0L, LocalDateTime.of(2022, Month.JULY, 18, 18, 18, 18))
    val chat2 = Chat("fakeID", "fakeName",false, 0L, LocalDateTime.of(2022, Month.JULY, 18, 18, 18, 20))

    val user = User(UUID.randomUUID(), "fakeuser")

    val printer1 = new MessagePrinter(user, chat1, List(user))
    val printer2 = new MessagePrinter(user, chat2, List(user))


    val value1 = messagePrinterReverseOrdering.compare(printer2, printer1)
    println(s"value = $value1")

    assert( value1 < 0 ,s"value is =< 0" )
  }

end MessagePrinterTests
