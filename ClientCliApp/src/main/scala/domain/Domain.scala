package com.github.malyszaryczlowiek
package domain

import java.util.UUID



object Domain {

  type Sender       = UUID
  type Interlocutor = UUID

  type ChatName = String
  type ChatId   = String
  type WritingId  = String

  def getTalksIds(sender: Sender, interlocutor: Interlocutor): (ChatId, WritingId) =
    val merged = s"$sender--$interlocutor"
    val talkTopicName = s"talk-$merged"
    val whoWriteTopicName = s"whoWrite-$merged"
    (talkTopicName, whoWriteTopicName)



}
