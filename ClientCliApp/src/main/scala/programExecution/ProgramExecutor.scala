package com.github.malyszaryczlowiek
package programExecution

import scala.annotation.tailrec
import scala.io.StdIn.{readChar, readInt, readLine}
import com.github.malyszaryczlowiek.messages.ChatManager

import java.io.Console
import scala.util.{Failure, Success, Try}

object ProgramExecutor :

  private var continueMainLoop = true

  def runProgram(args: Array[String]): Unit =
    var length = args.length
    while (continueMainLoop) {
      if length == 0 then
        println("Select what to do:\n1) Sign in,\n2) Sign up,\n3) Exit.")
        print("> ")
        Try { readInt() } match {
          case Failure(exception) => println("Please select (type) 1, 2 or 3.")
          case Success(value) =>
            if value == 1      then signIn()
            else if value == 2 then createAccount()
            else if value == 3 then continueMainLoop = false
            else () // do nothing simply start loop again
        }
      else if length == 1 then
        signInWithLogin(args.apply(0))
      else
        println(s"Warning!!! Too much program arguments: $length.")
        length = 0
    }


  private def signIn(): Unit =
    println("Type your login:")
    print("> ")
    signInWithLogin(readLine())


  @tailrec
  private def createAccount(): Unit =
    println("Login must have 8 characters at leased and must contain only letters, numbers, dash '-', or underscore '_':")
    print("> ")
    val login = readLine()
    val regex = "[\\w]{7,}".r
    if login == "#exit"          then continueMainLoop = false
    else if regex.matches(login) then setPassword()
    else
      println(s"Login is incorrect, try with another one, or type #exit.")
      createAccount()


  @tailrec
  private def setPassword(): Unit =
    val console: Console = System.console()
    if console != null then
      println("Set your Password:")
      print("> ")
      val pass1 = console.readPassword()      //("[%s]", "Password:")
      if pass1 != null then
        println("Please repeat the password:")
        print("> ")
        val pass2 = console.readPassword()    //("[%s]", "Password:")
        pass1.foreach(print)
        pass2.foreach(print)
        if pass2 != null && pass1.toSeq == pass2.toSeq then
          // probably must call gc() to remove pass1 and pass2
          println("Passwords matched.")


          // TODO tutaj kontynuować


        else
          println("Passwords do not match. Try Again.")
          setPassword()
      else
        println("Password was empty. Try with non empty.")
        setPassword()
    else
      println("Cannot read user input. Program termination.")
      continueMainLoop = false

      // java.util.Arrays.fill(passwd, ' ')



  private def signInWithLogin(login: String): Unit = ???






























    //  private var continueProgram = true
    //  private var continueSelectingChat = true
    //  private var selectChat = true
    //  private var searchUser = true
//    try {
//      val eDB: ExternalDB[Queryable] = new ExternalDB[PostgresStatement]()
//    }
//    finally {
//
//    }

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



