package com.github.malyszaryczlowiek
package unitTests.util

import kessengerlibrary.domain.Chat
import kessengerlibrary.domain.ChatGivens.given


import java.time.LocalDateTime
import java.time.temporal.{ChronoUnit, TemporalUnit}

class ChatGivensTests extends munit.FunSuite:


  test("testing sorting of Chats") {
    // Chat(chatId: ChatId, chatName: ChatName, groupChat: Boolean, offset: Long, timeOfLastMessage: LocalDateTime)
    val chat1: Chat = Chat("", "", false, 0L, LocalDateTime.now())
    val chat2: Chat = chat1.copy(timeOfLastMessage = chat1.timeOfLastMessage.plus(1L, ChronoUnit.HOURS))
    val chat3: Chat = chat1.copy(timeOfLastMessage = chat1.timeOfLastMessage.plus(2L, ChronoUnit.HOURS))

    val sorted: List[Chat]   = List(chat1, chat2, chat3)
    val unsorted: List[Chat] = List(chat3, chat2, chat1)

    val result = unsorted.sorted
    result.foreach(println)
    assert(result == sorted, s"sorted: $sorted, result: $result")
  }