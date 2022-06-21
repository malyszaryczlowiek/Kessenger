package com.github.malyszaryczlowiek
package util

import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime, OffsetDateTime}

/**
 * 
 * // https://docs.oracle.com/javase/tutorial/datetime/iso/timezones.html
 */
object TimeConverter :

  def fromMilliSecondsToLocal(milliSeconds: Long): LocalDateTime =
    LocalDateTime.ofInstant( Instant.ofEpochSecond(milliSeconds/1000L), ZoneId.systemDefault() )

  /**
   * 
   * @param local
   * @return
   */
  def fromLocalToEpochTime(local: LocalDateTime): Long =
    ZonedDateTime.ofLocal(local, ZoneId.systemDefault(), ZoneOffset.UTC).toEpochSecond  * 1000L



//    def fromUTCtoLocal(local: LocalDateTime): LocalDateTime =
//    //val instant: Instant = Instant.
//      LocalDateTime.
