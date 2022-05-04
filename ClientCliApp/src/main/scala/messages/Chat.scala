package com.github.malyszaryczlowiek
package messages

import domain.Domain.{ChatId, ChatName}

import java.time.LocalDateTime

case class Chat(chatId: ChatId, chatName: ChatName, groupChat: Boolean, offset: Long, timeOfLastMessage: LocalDateTime)
