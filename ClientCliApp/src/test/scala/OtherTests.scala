package com.github.malyszaryczlowiek

import kessengerlibrary.serdes.UserSerializer
import programExecution.ProgramExecutor

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


import scala.collection.parallel.mutable.ParTrieMap

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

end OtherTests
