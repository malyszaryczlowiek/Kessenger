package com.github.malyszaryczlowiek
package domain

import java.util.UUID

object Domain {

  type Sender       = UUID
  type Interlocutor = UUID
  type UserID       = UUID

  type Login     = String
  type Password  = String

  type ChatName  = String
  type ChatId    = String
  type WritingId = String
  type JoinId    = String

  def generateChatId(sender: Sender, interlocutor: Interlocutor): ChatId =
    s"chat--$sender--$interlocutor"


  def generateWritingId(sender: Sender, interlocutor: Interlocutor): WritingId =
    s"whoIsWriting--$sender--$interlocutor"


  def generateJoinId(user: UUID): JoinId =
    s"join--${user.toString}"

}
