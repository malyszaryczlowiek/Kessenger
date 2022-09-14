package io.github.malyszaryczlowiek
package encoders

import kessengerlibrary.domain.Domain.{ChatId, ChatName, Content, GroupChat, Login, MessageTime, ServerTime, StrUserID, ZoneId}

import java.sql.Timestamp



/**
 * Wrapper for Message class, but containing server_time as well.
 */
case class RichMessage(server_time: Timestamp, chat_id: ChatId, chat_name: ChatName, group_chat: GroupChat,
                       zone_id: ZoneId, message_time: Timestamp, content: Content, user_id: StrUserID,
                       login: Login)
