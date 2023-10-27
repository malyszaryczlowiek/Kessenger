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


  test("regex") {
    assert (java.util.regex.Pattern.matches("chat--[\\p{Alnum}-]*", "chat--541e7401-f332-4f21-9e1d-15a616e7ce3c--79df5513-28f5-437a-8ec8-c9e571e1e662") )
    assert (java.util.regex.Pattern.matches("chat--([\\p{Alnum}-]*)", "chat--541e7401-f332-4f21-9e1d-15a616e7ce3c--79df5513-28f5-437a-8ec8-c9e571e1e662") )
    //assert (java.util.regex.Pattern.matches("chat--([\\p{Alnum}-]*)", "chat--541e7401-f332-4f21-9e1d-15a616e7ce3c--79df5513-28f5-437a-8ec8-c9e571e1e662") )

  }

}
