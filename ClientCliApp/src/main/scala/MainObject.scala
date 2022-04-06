package com.github.malyszaryczlowiek

import scala.annotation.tailrec
import scala.io.StdIn.{readChar, readLine}
import com.github.malyszaryczlowiek.db.DB

import scala.util.control.Breaks.break

object MainObject:

  private def readMessage(): String =
    val sb: StringBuilder = StringBuilder()
    try {
      var readNext = true
      while (readNext) {
        val c = readChar()
        if c == '\n' then
          readNext = false
        else
          // TODO here send info to topic whoWrite
          sb.append(c)
      }
      sb.toString()
    }
    catch
      case e: java.io.EOFException => "no message"
      case e: java.lang.StringIndexOutOfBoundsException => sb.toString()




  @tailrec
  def programExecution(): Unit =
    Thread.sleep(100)
    println("App running")




    programExecution()








  def main(args: Array[String]): Unit =

    println("Type your second name:")
    print("> ")
    val name = readLine()

    var continueProgram = true
    while (continueProgram) {
      DB.searchUser(name) match {
        case Some(user) =>
          println(s"$name your talks:")
          DB.getUsersTalks(user).foreach( tutaj wypisz talki)
          ERROR
        case None =>
          println(s"Incorrect name: \"$name\". There is no such user in the system.")
          println(s"Would you like to continue or exit program? Type any key to continue or \"exit\" to end program. ")
          print("> ")
          if readLine() == "exit" then
            continueProgram = false
          else
            println("Select your name again:")
            print("> ")
            val name = readLine()
      }
    }
    println("Bye bye \uD83D\uDC4B")
