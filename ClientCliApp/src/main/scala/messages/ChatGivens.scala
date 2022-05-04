package com.github.malyszaryczlowiek
package messages

object ChatGivens :
  given orderChatWithDate: Ordering[Chat] with
    override def compare(x: Chat, y: Chat): Int =
      val timeX = x.timeOfLastMessage
      val timeY = y.timeOfLastMessage
      if timeX.isBefore(timeY) then -1
      else if timeX.isAfter(timeY) then 1
      else 0




  // for tests purposes
//  given orderChatWithOffset: Ordering[Chat] with
//    override def compare( x: Chat, y: Chat): Int = 0
