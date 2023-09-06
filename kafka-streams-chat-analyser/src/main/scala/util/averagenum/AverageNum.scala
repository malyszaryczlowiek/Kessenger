package io.github.malyszaryczlowiek
package util.averagenum

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

case class AverageNum(nominator: Long = 0L, denominator: Long = 0L) {

  override def toString: String = {
    val nominator2 = BigDecimal.apply(s"${nominator}").setScale(0)
    val den = if (denominator == 0L) 1L else denominator
    val denominator2 = BigDecimal.apply(s"$den").setScale(0)
    nominator2./(denominator2).setScale(2, BigDecimal.RoundingMode.HALF_EVEN).toString()
  }

}


object AverageNum {

  implicit object encoder extends Encoder[AverageNum] {
    override def apply(a: AverageNum): Json =
      Json.obj(
        ("nominator",   Json.fromLong(a.nominator)),
        ("denominator", Json.fromLong(a.denominator))
      )
  }

  implicit object decoder extends Decoder[AverageNum] {
    override def apply(c: HCursor): Result[AverageNum] = {
      for {
        nominator   <- c.downField("nominator").as[Long]
        denominator <- c.downField("denominator").as[Long]
      } yield {
        AverageNum(nominator, denominator)
      }
    }

  }

  def add(old: AverageNum, num: Long): AverageNum =
    old.copy(old.nominator + num, old.denominator + 1L)


}


