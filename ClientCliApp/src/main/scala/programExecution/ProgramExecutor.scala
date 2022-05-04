package com.github.malyszaryczlowiek
package programExecution

import java.io.Console
import scala.util.{Failure, Success, Try}
import scala.annotation.tailrec
import scala.io.StdIn.{readChar, readInt, readLine}
import com.github.malyszaryczlowiek.account.MyAccount
import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, Password}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, ChatManager}
import com.github.malyszaryczlowiek.util.PasswordConverter

import scala.collection.immutable


object ProgramExecutor :

  private var continueMainLoop = true


  def runProgram(args: Array[String]): Unit =
    var length = args.length
    while (continueMainLoop) {
      if length == 0 then
        println("Select what to do:\n1) Sign in,\n2) Sign up,\n3) Exit.")
        print("> ")
        Try { readInt() } match {
          case Failure(exception) => println("Please select 1, 2 or 3.")
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
    println("Type your login, or #exit:")
    print("> ")
    signInWithLogin(readLine())


  @tailrec
  private def signInWithLogin(login: Login): Unit =
    val console: Console = System.console()
    if console != null then
      println("Type your password:")
      print("> ")
      val pass = console.readPassword()      //("[%s]", "Password:")
      if pass != null then
        lazy val password = pass.mkString("")
        PasswordConverter.convert(password) match {
          case Left(_)   =>
            println(s"Ooops some Error.")
            signInWithLogin(login)
          case Right(ep) => checkCredentials(login, ep)
        }
      else
        println("Some error occurred, try again.")
        signInWithLogin(login)
    else
      println("OOOps problem with Console. Cannot use it. Program Terminates.")



  private def checkCredentials(login: Login, password: Password): Unit =
    ExternalDB.findUser(login, password) match {
      case Left(QueryErrors(l @ List(QueryError(queryErrorType, description)))) =>
        println(s"${description}")
        signIn()
      case Right(user) => // user found
        MyAccount.initialize(user)
        printMenu()
      case _           =>
        println("Other problem.")
        signIn()
    }



  @tailrec
  private def createAccount(): Unit =
    println("Login must have 8 characters at leased and must contain only letters, numbers or underscore '_'. If you want exit type #exit:")
    print("> ")
    val login = readLine()
    val regex = "[\\w]{8,}".r
    if login == "#exit"          then continueMainLoop = false
    else if regex.matches(login) then setPassword(login)
    else
      println(s"Login is incorrect, try with another one, or type #exit.")
      createAccount()



  @tailrec
  private def setPassword(login: Login): Unit =
    val console: Console = System.console()
    if console != null then
      println("Set your Password:")
      print("> ")
      val pass1 = console.readPassword()
      if pass1 != null then
        println("Please repeat the password:")
        print("> ")
        val pass2 = console.readPassword()
        pass1.foreach(print)
        pass2.foreach(print)
        if pass2 != null && pass1.toSeq == pass2.toSeq then
          // probably must call gc() to remove pass1 and pass2
          println("Passwords matched.")
          PasswordConverter.convert(pass2.mkString("")) match {
            case Left(value) =>
              println("Undefined error, try again")
              setPassword(login)
            case Right(value) =>
              ExternalDB.createUser(login, value) match {
                case Left(QueryErrors(l @ List(QueryError(queryErrorType, description)))) =>
                case Right(user: User) =>
                  MyAccount.initializeAfterCreation(user)
                  printMenu()
                case _ =>
                  println("Undefined error.")
                  setPassword(login)
              }
          }
        else
          println("Passwords do not match. Try Again.")
          setPassword(login)
      else
        println("Password was empty. Try with non empty.")
        setPassword(login)
    else
      println("Cannot read user input. Program termination.")
      continueMainLoop = false



  @tailrec
  private def printMenu(): Unit =
    val invitations = ChatManager.numberOfInvitations
    println(s"Menu:")
    println(s"1) Show my chats.")
    println(s"2) Create new chat")
    println(s"3) Settings.")
    println(s"4) Exit.")
    print("> ")
    Try {
      readInt()
    } match {
      case Failure(exception) => println("Please select 1, 2, 3 or 4.")
      case Success(value) =>
        if value == 1 then
          showChats()
          printMenu()
        else if value == 2 then
          createChat()  // TODO  implement
        else if value == 3 then
          showSettings() // TODO  implement
        else if value == 4 then
          continueMainLoop = false
        else
          println("Wrong number, please select 1, 2, 3 or 4.")
          printMenu()
    }



  @tailrec
  private def showChats(): Unit =
    println(s"Please select chat, or type #back to menu.")
    println("Your chats:")
    val chats: immutable.SortedMap[Chat, ChatExecutor] = MyAccount.getMyChats
    val chatSize = chats.size
    if chats.isEmpty then println(s"Ooopsss, you have not chats.")  // and we return to menu
    else
      val sorted = chats.toSeq.sortBy(chatAndExecutor => chatAndExecutor._2.getLastMessageTime).reverse
      val indexed = sorted.zipWithIndex
      indexed.foreach(
        chatIndex => {
          // if offset is different than 0 means that user read from chat so accept them
          if chatIndex._1._1.offset == 0L then
            println(s"${chatIndex._2 + 1}) ${chatIndex._1._1.chatName} (NOT ACCEPTED)")
          else
            println(s"${chatIndex._2 + 1}) ${chatIndex._1._1.chatName}")
        })
      print("> ")
      val input: String = readLine()
      if input == "#back" then () // do nothing simply return to printMenu() method
      else
        input.toIntOption match {
          case Some(value) =>
            if value < 1 || value > chatSize + 1 then
              println(s"Please select number between 1 and $chatSize,\n" +
                s"or type #back if you want return to main menu.")
              showChats()
            else
              val chatAndChatExecutor = indexed.toVector.apply(value-1)._1
              workInChat(chatAndChatExecutor._1, chatAndChatExecutor._2)
              showChats()
          case None =>
            println(s"Oppsss, your input does not match integer number or '#back'," +
              s"or #escape_chat if you do not want participate")
            showChats()
          }



  /**
   * this method must start kafka producer and consumer
   */
  private def workInChat(chat: Chat, executor: ChatExecutor): Unit =
    if chat.groupChat then
      println(s"you are in ${chat.chatName}, type '#back' to return to chat list\n " +
        s"or #escape_chat if you do not want participate longer.")
    else
      println(s"you are in ${chat.chatName}, type '#back' to return to chat list")
    executor.printUnreadMessages()
    print("> ")
    Try {
      var line = readLine()
      while (line != "#back") {
        if chat.groupChat && line == "#escape_chat" then
          escapeChat(executor)
          line = "#back"
        else
          executor.sendMessage(line)
          print("> ")
          line = readLine()
      }
      executor.stopPrintingMessages()
    } match {
      case Failure(exception) => println(s"Unexpected Error in chat.")
      case Success(value)     => () // do nothing simply return to chat list
    }




  /**
   * In this method we sent notification to other users,
   * who should participate the chat.
   *
   */
  private def createChat(): Unit = ???


  
  
  private def escapeChat(executor: ChatExecutor): Unit =
    val me = executor.getUser
    ExternalDB.deleteMeFromChat( me, executor.getChat) match {
      case Left(queryErrors: QueryErrors) =>
        queryErrors.listOfErrors.foreach(qe => println(qe.description))
        println(s"Try again later.")
      case Right(chat: Chat)                    =>
        println(s"You escaped chat ${chat.chatName}.")
        executor.sendMessage(s"## ${me.login} Stopped participating in chat. ##")
        executor.closeChat()
        MyAccount.removeChat(chat) match {
          case Some(_) => println(s"Chat ${chat.chatName} removed from list.")
          case None    => println(s"Undefined Error, Cannot remove chat from list.")
        }
    }


  private def showSettings(): Unit = ???
