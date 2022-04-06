package com.github.malyszaryczlowiek
package domain

import java.util.UUID

case class User(userId: UUID, secondName: String, firstName: String)

object Domain {

  type Sender       = UUID
  type Interlocutor = UUID


  Zmień talk na Chat
  type TalkName = String
  type TalkId   = String
  type WriteId  = String

  def getTalksIds(sender: Sender, interlocutor: Interlocutor): (TalkId, WriteId) =
    val merged = s"$sender--$interlocutor"
    val talkTopicName = s"talk-$merged"
    val whoWriteTopicName = s"whoWrite-$merged"
    (talkTopicName, whoWriteTopicName)



}
