package io.github.malyszaryczlowiek
package unitTests.messages

import kessengerlibrary.domain.Domain.{ChatName, Login, UserID}
import kessengerlibrary.domain.User

import java.time.LocalDateTime
import collection.parallel.CollectionConverters.RangeIsParallelizable
import collection.parallel.CollectionConverters.MutableMapIsParallelizable
import scala.collection.mutable
import scala.collection.parallel.mutable.ParMap


import java.util.UUID
import scala.util.Random




class ChatExecutorTests extends munit.FunSuite {

  test("Clearing ParMap gives Empty Map") {
    val unreadMessages: ParMap[Long, String] = mutable.Map.empty[Long, String].par
    for
      i <- (0 until 10).par
    do
      unreadMessages.addOne((Random.nextLong(100L), ""))
    unreadMessages.foreach(println)
    // assert(unreadMessages.size == 10 , s"size is: ${unreadMessages.size}") // this assertions not pass
    println("next seq")
    val seq = unreadMessages.seq.to(mutable.SortedMap) // conversion to non parallel sequence
    seq.foreach(println)

    unreadMessages.clear()
    assert(unreadMessages.isEmpty, s"size is: ${unreadMessages.size}")
  }


  test("Is sorted map pre sorted?") {
    val unreadMessages: ParMap[Long, String] = mutable.SortedMap.empty[Long, String].par
    for
      i <- (0 until 10).par
    do
      unreadMessages.addOne((Random.nextLong(100L), ""))
    unreadMessages.foreach(println)
    // assert(unreadMessages.size == 10 , s"size is: ${unreadMessages.size}") // this assertions not pass
    println("next seq")
    val sorted = unreadMessages.seq.to(mutable.SortedMap) // conversion to non parallel sequence
    sorted.foreach(println)
    assert(sorted != unreadMessages)
  }

}
