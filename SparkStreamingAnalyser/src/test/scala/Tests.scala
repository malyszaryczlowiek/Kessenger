package io.github.malyszaryczlowiek

import io.github.malyszaryczlowiek.db.DbTable

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

  // error
  // napisać testy sprawdzające wszystkie nowo napisane klasy,
  // tak czy wszystko jest parsowane i przetwarzane poprawnie

  test("testing DBTable.getTableColumnsWithTypes") {

    val avgServerDelayByUser = DbTable("avg_server_delay_by_user",
      Map(
        "window_start" -> "timestamp",
        "window_end" -> "timestamp",
        "user_id" -> "uuid",
        "delay_by_user" -> "BIGINT",
      )
    )

    println( s"${avgServerDelayByUser.getTableColumnsWithTypes}" )

  }

}
