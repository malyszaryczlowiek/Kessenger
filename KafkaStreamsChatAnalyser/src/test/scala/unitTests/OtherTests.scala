package io.github.malyszaryczlowiek
package unitTests

import kessengerlibrary.domain.Domain

import java.util.UUID
import java.util.regex.Pattern

class OtherTests extends munit.FunSuite {




  test("Test topic regex matching") {
    val chatName = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
    val result = Pattern.matches(s"chat-[\\p{Alnum}-]*", chatName)
    assert(result, s"not match")
  }





  test("testing of decimals") {
    val s = BigDecimal("123.456")
    println(s"${s.setScale(1,BigDecimal.RoundingMode.HALF_EVEN)}")
    var i = BigInt("123")
    println(s"${BigDecimal(i).setScale(1,BigDecimal.RoundingMode.HALF_EVEN)}")
    i = i.+(BigInt(100))
    println(s"${BigDecimal(i).setScale(1,BigDecimal.RoundingMode.HALF_EVEN)}")
  }

}
