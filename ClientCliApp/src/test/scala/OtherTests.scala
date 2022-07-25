package com.github.malyszaryczlowiek

import scala.collection.parallel.mutable.ParTrieMap

class OtherTests extends munit.FunSuite {

  test("String evaluation test") {

    val ex = true

    val toPrint = "Hello"

    println(s"${if ex then toPrint}")

  }

  test("addition pair if key exists") {

    val map: ParTrieMap[Long, String] = ParTrieMap.empty

    map.addOne(1L -> "first")
    map.addOne(1L -> "second")

    map.get(1L) match {
      case Some(value) => assert(value == "second")
      case None => throw new Exception(s"value should be returned")
    }


  }

}
