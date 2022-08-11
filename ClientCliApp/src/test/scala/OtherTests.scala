package com.github.malyszaryczlowiek

import kessengerlibrary.serdes.UserSerializer
import programExecution.ProgramExecutor

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import scala.collection.parallel.mutable.ParTrieMap
import scala.collection.concurrent.TrieMap

class OtherTests extends munit.FunSuite:

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


  test("print name") {
    val serializer = classOf[UserSerializer].getClass.getName
    println(serializer)
  }

  test("logger name") {

    val name = classOf[ProgramExecutor]


    println(name)
  }

  test("logging testing") {

    val  logger = LogManager.getLogger("ProgramExecutor")
    val  fake = LogManager.getLogger("Fake")
      //LoggerFactory.getLogger()

    logger.debug("Debug Message Logged !!!")
    logger.info("Info Message Logged !!!")
    logger.error("Error Message Logged !!!", new NullPointerException("NullError"))

    fake.info("fake info.")
  }


  test("Is TrieMap mutable?") {
    val map: TrieMap[Long, Long] = TrieMap.empty

    map.addOne(0L -> 0L)

    assert(map.nonEmpty)

    map.get(0L) match {
      case Some(value) => assert(value == 0L)
      case None => assert(false, s"should return some value")
    }

  }


  test("folding sql statement") {

    val offset: TrieMap[Long, Long] = TrieMap(1L -> 6L, 0L -> 7L)
        val off = offsets.toSeq.sortBy(_._1) // sort by key (number of partition)
    val prefix = "UPDATE users_chats SET "
    val middle = off.foldLeft[String]("")(
      (folded: String, partitionAndOffset: (Long, Long)) =>
        s"${folded}users_offset_${partitionAndOffset._1} = ?, "
    )
    val postfix = " message_time = ? WHERE chat_id = ? AND user_id = ? "
    val sql = s"$prefix$middle$postfix"

    println(sql)



  }

end OtherTests
