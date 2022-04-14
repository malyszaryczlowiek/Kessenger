package com.github.malyszaryczlowiek
package messages

import domain.Domain.{ChatId, ChatName}

case class Chat(chatId: ChatId, chatName: ChatName)
