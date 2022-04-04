package com.github.malyszaryczlowiek

object MainObject:
  def main(args: Array[String]): Unit =

    while (true) {
      Thread.sleep(1_000)
      println("App running")
    }

    println("ClientCliApp closed")
