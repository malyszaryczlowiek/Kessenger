package io.github.malyszaryczlowiek

import programExecution.ProgramExecutor

import scala.util.control.Breaks.break

object MainObject:

  def main(args: Array[String]): Unit =
    ProgramExecutor.runProgram(args)
    println("Bye bye \uD83D\uDC4B")
