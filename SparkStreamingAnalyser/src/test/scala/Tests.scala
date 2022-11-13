package io.github.malyszaryczlowiek

import java.sql.Timestamp
import java.time. LocalDateTime

class Tests extends munit.FunSuite {

  test("Conversion of TimeStamp") {
    val t    = Timestamp.valueOf("2022-09-22 22:33:13") // 'yyyy-MM-dd hh:mm:ss'
    val newT = Timestamp.valueOf(LocalDateTime.of(2022, 9, 22, 22, 33, 13))
    assert( t.equals(newT) )
  }

}
