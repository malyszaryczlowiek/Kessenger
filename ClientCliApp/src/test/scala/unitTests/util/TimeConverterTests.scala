package com.github.malyszaryczlowiek
package unitTests.util

import java.time.LocalDateTime
import com.github.malyszaryczlowiek.util.TimeConverter

import java.time.temporal.ChronoUnit

class TimeConverterTests extends  munit.FunSuite :

  test("testing time conversions") {

    val now                  = System.currentTimeMillis() // UTC time
    val local: LocalDateTime = TimeConverter.fromMilliSecondsToLocal(now)
    val newNow: Long         = TimeConverter.fromLocalToEpochTime(local)
    val removed: Long        = (now/1000L) * 1000L     // remove info about milliseconds
    val isTrue               = removed == newNow
    assert(isTrue, s" times: $removed and $newNow ")

  }

  test ("Simple LocalDateTime sorting") {
    val t1 = LocalDateTime.now()
    val t2 = t1.plus(1L, ChronoUnit.HOURS)
    val t3 = t1.plus(2L, ChronoUnit.HOURS)

    val sorted: List[LocalDateTime]   = List(t1, t2, t3)
    val unsorted: List[LocalDateTime] = List(t3, t2, t1)

    val result = unsorted.sorted
    result.foreach(println)
    assert(result == sorted, s"sorted: $sorted, result: $result")

  }
