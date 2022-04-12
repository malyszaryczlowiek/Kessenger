package com.github.malyszaryczlowiek
package db

import com.github.malyszaryczlowiek.domain.PasswordConverter

import java.sql.Connection
import scala.util.{Failure, Success}
import sys.process.*


/**
 * Integration tests for DB. Each test is run on separate
 * preinitialized DB build in docker container.
 */
class DatabaseTests extends munit.FunSuite:

  private var cd: DataBase = _
//
//  override def beforeEach(context: BeforeEach): Unit =
//    val outputOfDockerRestarting = "./initializeDB".!!
//    Thread.sleep(5000)
//    println(outputOfDockerRestarting)
//    println("Database prepared")
//    cd = new ExternalDB()
//
//  override def afterEach(context: AfterEach): Unit =
//    cd.closeConnection() match {
//      case Failure(exception) => println(exception.getMessage)
//      case Success(value) => println("connection closed properly")
//    }




  test("Testing user insertion"){
    cd = new ExternalDB()
    cd.createUser("name", "pass") match {
      case Failure(exception) =>
        println(exception.getMessage)
        assert(false, "ERROR BAZY: " + exception.getMessage)
      case Success(value) =>
        value match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == "name")
        }
    }
  }

  test("Testing user searching by login"){
    cd = new ExternalDB()
    cd.findUser("Spejson" ) match {
      case Failure(exception) =>
        assert(false, "ERROR BAZY: " + exception.getMessage)
      case Success(value) =>
        value match {
          case Left(value) => assert(false, value.description)
          case Right(dbUser) => assert(dbUser.login == "Spejson")
        }
    }
  }
