package io.github.malyszaryczlowiek
package unitTests

import kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.util.averagenum.AverageNum

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


  test("testing strings and regex") {

    val input = s"one, two, three, "
    input.replaceAll(s"", " ").trim.split(s"\\W")

  }


//  test("AverageWordNum 1") {
//    val calculator = new AverageWordsNum
//    calculator.add(100)
//    calculator.add(20)
//
//    val result = calculator.getAverage.toString()
//    assertEquals(result, s"60.00")
//
//  }
//
//  test("AverageWordNum 1") {
//    val calculator = new AverageWordsNum
//    calculator.add(1)
//    calculator.add(2)
//
//    val result = calculator.getAverage.toString()
//    assertEquals(result, s"1.50")
//
//  }

}
