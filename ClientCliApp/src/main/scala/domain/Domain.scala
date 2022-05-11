package com.github.malyszaryczlowiek
package domain

import java.util.UUID

object Domain {

  type UserID    = UUID
  type Login     = String
  type Password  = String
  type ChatName  = String
  type ChatId    = String
  type WritingId = String
  type JoinId    = String


  def generateChatId(sender: UUID, interlocutor: UUID): ChatId =
    s"chat--$sender--$interlocutor"


  def generateWritingId(sender: UUID, interlocutor: UUID): WritingId =
    s"whoIsWriting--$sender--$interlocutor"


  def generateJoinId(user: UUID): JoinId =
    s"join--${user.toString}"

}
