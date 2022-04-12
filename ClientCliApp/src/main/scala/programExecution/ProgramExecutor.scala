package com.github.malyszaryczlowiek
package programExecution

import com.github.malyszaryczlowiek.db.queries.Queryable

import scala.annotation.tailrec
import scala.io.StdIn.{readChar, readInt, readLine}
import com.github.malyszaryczlowiek.db.{DataBase, ExternalDB, InMemoryDB}
import com.github.malyszaryczlowiek.domain.{Domain, User}
import com.github.malyszaryczlowiek.domain.Domain.{ChatId, ChatName, WritingId}
import com.github.malyszaryczlowiek.messages.ChatManager

object ProgramExecutor :

//  private var continueProgram = true
//  private var continueSelectingChat = true
//  private var selectChat = true
//  private var searchUser = true

  def runProgram(): Unit =
//    try {
//      val eDB: ExternalDB[Queryable] = new ExternalDB[PostgresStatement]()
//    }
//    finally {
//
//    }

    println("Type your second name:")
    print("> ")
    val name = readLine()
//    while (continueProgram)
//      selectUser(name)
//    closeAllChats()

  /**
   * In this method we select user to chat with.
   * @param name
   */
//  @tailrec
//  private def selectUser(name: String): Unit =
//    InMemoryDB.searchUser(name) match
//      case Some(me) =>
//        println(s"$name chats list:")
//        val chats: Vector[(Int, ChatId, ChatName)] = InMemoryDB.getUsersChats(me)  // TODO recursive calling to db highly inappropriate
//        selectChat(me, chats)
//      case None =>
//        println(s"Incorrect name: \"$name\". There is no such user in the system.")
//        println(s"Would you like to continue or exit program? Type any key to continue or \"#exit\" to end program. ")
//        print("> ")
//        if readLine == "#exit" then
//          continueProgram = false
//        else
//          println("Select your name again:")
//          print("> ")
//          val name = readLine()
//          selectUser(name)
//
//
//  private def selectChat(me: User, chats:  Vector[(Int, ChatId, ChatName)]): Unit =
//    val size = chats.length
//    while (selectChat) {
//      chats.foreach((index, chatId, chatName) => println(s"$index) $chatName"))
//      println("select chat, or type 0 to create new one, or #exit if you want leave app:")
//      print("> ")
//      try {
//        val input: String = readLine()
//        if input == "#exit" then
//          selectChat = false
//          continueProgram = false
//        else
//          val number = input.toInt
//          if number > 0 && number < size then
//            val chatId: ChatId = chats(number-1)._2
//            println("Opening chat...")
//            continueChat(me, chatId)
//          else if number == 0 then
//            while (searchUser)
//              searchUser(me)
//          else
//            println(s"Incorrect number: $number. Try number between 0 and $size (exclusive), or exit to leave app.")
//      }
//      catch
//        case e: java.lang.NumberFormatException => println(s"Incorrect format. Try number between 0 and $size (exclusive), or exit to leave app.")
//    }
//
//
//  private def searchUser(me: User): Unit =
//    println("Type user to connect with:")
//    print("> ")
//    val user: String = readLine()
//    InMemoryDB.searchUser(user) match {
//      case Some(interlocutor) =>
//        println(s"Type message to send, or '#exit' to leave chat.")
//        print("> ")
//        val message = readLine()
//        ChatManager.askToJoinChat(me.userId, interlocutor.userId, message)
//        val chatId: ChatId = Domain.generateChatId(me.userId, interlocutor.userId)
//        continueChat(me, chatId)
//      case None =>
//        println(
//          s"""There is no such user: \"$user\", would you like search again? -> type 'yes',
//             |Stop searching and go back to select chat? -> type 'chats',
//             |Do you want leave program? -> type any other sequence.""".stripMargin)
//        print("> ")
//        val whatToDo = readLine()
//        if whatToDo  == "yes" then ()
//        else if whatToDo == "chats" then
//          searchUser = false
//        else
//          searchUser = false
//          selectChat = false
//          continueProgram = false
//    }
//
//
//
//  @tailrec
//  private def continueChat(me: User, chatId: ChatId): Unit =
//    val yourMessage = readLine()
//    if yourMessage == "#exit" then ()
//    else if yourMessage.isEmpty then continueChat(me, chatId)
//    else
//      ChatManager.sendMessage(me.userId, chatId, yourMessage)
//      continueChat(me, chatId)
//
//  private def closeAllChats(): Unit =
//    ChatManager.closeAll()
//    println("All chats closed!")



