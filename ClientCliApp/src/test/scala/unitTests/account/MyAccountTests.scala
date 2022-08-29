package io.github.malyszaryczlowiek
package unitTests.account

import scala.collection.parallel.mutable.ParTrieMap

class MyAccountTests extends munit.FunSuite :

  test("Checking if addition to ParTrieMap replace existing value".fail) {
    val map: ParTrieMap[String, String] = ParTrieMap.empty
    map.addOne("key1" -> "val1")
    map.addOne("key1" -> "val2")

    // Watch out adding with existing key replace value
    // this assertion fails!!!
    assert(map.getOrElse("key1", "Not value") == "val1")

  }
