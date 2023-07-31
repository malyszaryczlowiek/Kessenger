package io.github.malyszaryczlowiek

import java.math.MathContext
import java.sql.Timestamp
import java.time.LocalDateTime
import scala.math.BigDecimal.RoundingMode

class Tests extends munit.FunSuite {

  test("Conversion of TimeStamp") {
    val t    = Timestamp.valueOf("2022-09-22 22:33:13") // 'yyyy-MM-dd hh:mm:ss'
    val newT = Timestamp.valueOf(LocalDateTime.of(2022, 9, 22, 22, 33, 13))
    assert( t.equals(newT) )
  }


  test("testing conversion") {
    val big = BigDecimal.decimal(1234.5678).setScale(0, RoundingMode.HALF_UP)
    val avg = big.toLong
    println(s"$avg")

  }
}
