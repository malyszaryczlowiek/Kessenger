package com.github.malyszaryczlowiek
package programExecution


import account.MyAccount
import db.ExternalDB
import messages.{ChatManager, KessengerAdmin, MessagePrinter}
import util.{ChatNameValidator, PasswordConverter}

import kessengerlibrary.db.queries.{QueryError, QueryErrors}
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.domain.Domain.{Login, Password}
import kessengerlibrary.kafka.configurators.KafkaProductionConfigurator
import kessengerlibrary.kafka.configurators.KafkaConfigurator
import kessengerlibrary.kafka.errors.KafkaError
import kessengerlibrary.status.Status
import kessengerlibrary.status.Status.{Closing, NotInitialized, Running, Starting, Terminated}

import java.io.Console
import java.util.concurrent.atomic.AtomicBoolean
import scala.::
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.io.StdIn.{readChar, readInt, readLine}
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global

class ProgramExecutor

object ProgramExecutor :

  private var manager: ChatManager = _
  private var me: User             = _
  private val executeHook: AtomicBoolean = new AtomicBoolean(true)



  @tailrec
  def runProgram(args: Array[String]): Unit =

    // if we want to force close program we should close all connections
    // ass well

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      if executeHook.get() then
        logout()
        closeProgram()
    }))




    val length = args.length
    if length == 0 then
      println(s"Kessenger v0.1.2")
      println("Select what to do:\n1) Sign in,\n2) Create Account,\n3) Exit.")
      print("> ")
      Try { readInt() } match {
        case Failure(exception) =>
          println("Please select 1, 2 or 3.")
          runProgram(args)
        case Success(value) =>
          if      value == 1 then
            signIn()
            runProgram(Array.empty)
          else if value == 2 then
            createAccount()
          else if value == 3 then
            closeProgram()
          else
            println("Please select 1, 2 or 3.")
            runProgram(args)
      }
    else if length == 1 then
      signInWithLogin(args.apply(0))
      runProgram(Array.empty[String])
    else
      println(s"Error!!! Too much program arguments: $length.")
      println("Program exit.")




  private def signIn(): Unit = signInWithLogin("")




  @tailrec
  private def signInWithLogin(log: Login): Unit =
    println("Type your login, or #exit:")
    print("> ")
    var login: Login = ""
    if log.isEmpty then login = readLine()
    else login = log
    // validate if login does not match punctation characters:
    // !#%&'()*+,-./:;<=>?@[\\]^_`{|}~.\"$
    // and does not contain only numbers
    val loginRegex = "([\\p{Alnum}]*[\\p{Punct}]+[\\p{Alnum}]*)|([0-9]+)".r
    //if so we need to repeat question.
    if "".equals(login) then
      println(s"Login cannot be empty.")
      signInWithLogin("")
    else if "#exit".equals(login) then
      {} // back to main menu program
    else if loginRegex.matches(login) then
      println("Login cannot contain punctuation characters: !#%&'()*+,-./:;<=>?@[\\]^_`{|}~.\"$")
      signInWithLogin("")
    else
      val console: Console = System.console()
      if console != null then
        println("Type your password:")
        print("> ")
        lazy val pass = console.readPassword()
        if pass != null then
          ExternalDB.findUsersSalt(login) match { // TODO here is problem if we do not have connection to DB
            case Right(salt) =>
              lazy val password = pass.mkString("")
              PasswordConverter.convert(password, salt) match {
                case Left(_)   =>
                  println(s"Ooops some Error.")
                  signInWithLogin("")
                case Right(ep) =>
                  checkCredentials(login, ep, salt)
              }
            case Left(queryErrors: QueryErrors) =>
              queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
              signInWithLogin("")
          }
        else
          println("Some error occurred, try again.")
          signInWithLogin("")
      else
        println("OOOps problem with Console. Cannot use it.")




  /**
   *
   * @param login
   * @param password
   * @param salt
   */
  private def checkCredentials(login: Login, password: Password, salt: String): Unit =
    ExternalDB.findUser(login, password, salt) match {
      case Left(queryErrors: QueryErrors) =>
        queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
        signIn()
      case Right(user) => // user found
        me = user.copy(salt = None)
        KafkaConfigurator.setConfigurator(new KafkaProductionConfigurator)
        KessengerAdmin.startAdmin(KafkaConfigurator.configurator)
        MyAccount.initialize(user) match {
          case Left(errorsTuple) =>
            errorsTuple match {
              case (Some(dbErrors), None)     =>
                // we print the error
                dbErrors.listOfErrors.foreach(error => println(s"${error.description}"))
              // and after printing move back to main menu
              case (None, Some(kafkaError))  =>
                // we print error and move back to main manu
                println(s"${kafkaError.description}")
                // and after printing move back to main menu
              case _ =>
                println(s"Some undefined error.")
            }
          case Right(chatManager: ChatManager) =>
            manager = chatManager
            printMenu()
        }
    }




  /**
   *
   */
  @tailrec
  private def printMenu(): Unit =
    println(s"Menu:")
    println(s"1) Show my chats.")
    println(s"2) Create new chat")
    println(s"3) Settings.")
    println(s"4) Log out and back to main Menu.")
    print("> ")
    Try {
      readInt()
    } match {
      case Failure(exception) =>
        println("Please select 1, 2, 3 or 4.")
        printMenu()
      case Success(value) =>
        if value == 1 then
          showChats()
          printMenu()
        else if value == 2 then
          createChat()
          printMenu()
        else if value == 3 then
          showSettings() // TODO  implement
          printMenu()
        else if value == 4 then
          logout()
        else
          println("Wrong number, please select 1, 2, 3 or 4.")
          printMenu()
    }




  @tailrec
  private def showChats(): Unit =
    val chats: List[Chat] = manager.getUsersChats
    val chatSize = chats.size
    if chats.isEmpty then println(s"Ooopsss, you have not chats.")  // and we return to menu
    else
      println(s"Please select chat, or type #back to menu.")
      println("Your chats:")
      val indexedList = chats.zipWithIndex
      indexedList.foreach(
        (chatIndex: (Chat, Int)) => {
          val numOfUnreadMessages = manager.getNumOfUnreadMessages(chatIndex._1)

          if numOfUnreadMessages > 0L then
            println(s"${chatIndex._2 + 1}) ${chatIndex._1.chatName} ($numOfUnreadMessages new message(s)")
          else
            println(s"${chatIndex._2 + 1}) ${chatIndex._1.chatName}")
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
              val selectedChat = indexedList.apply(value - 1)._1
              workInChat(selectedChat)
              showChats()
          case None =>
            println(s"Oppsss, your input does not match integer number between 1 and ${indexedList.length} or '#back'")
              //s"or #escape_chat if you do not want participate")
            showChats()
          }




  /**
   * this method must start kafka producer and consumer
   */
  private def workInChat(chat: Chat): Unit =
    manager.getMessagePrinter(chat) match {
      case Some(messagePrinter: MessagePrinter) =>
        if chat.groupChat then
          println(s"You are in \'${chat.chatName}\' chat, type your messages, or type '#back' to return to chat list\n"
            + "or '#escape_chat if you do not want participate longer in group chat.")
        else
          println(s"You are in '${chat.chatName}' chat, type your messages, or type '#back' to return to chat list.")

        print(s"> ")
        // now we print buffered messages and then print all incoming messages
        messagePrinter.printUnreadMessages()
        Try {
          var line = readLine()
          while (line != "#back") {
            if chat.groupChat && line == "#escape_chat" then
              escapeChat(chat)
              line = "#back"
            else
              manager.sendMessage(chat, line)
              print("> ")
              line = readLine()
          }
          // when we want stop participating in chat at the moment
          // we stop printing messages.
          // MessagePrinter will start collecting messages to buffer again
          messagePrinter.stopPrintMessages()

          // for clarity we check printer status.
          messagePrinter.getStatus
        } match {
          case Failure(exception) =>
            // some other unexpected error
            print(s"Unexpected Error in chat.\n> ")
          case Success(status: Status) =>
            print(s"Status messagePrintera po zamknięciu chatu $status\n> ") // TODO DELETE
        }
      case None =>
        {} // not reachable
    }




  /**
   * In this method we sent notification to other users,
   * who should participate the chat.
   *
   */
  @tailrec
  private def createChat(): Option[Chat] =
    val bufferUsers   = ListBuffer.empty[User]
    val selectedUsers = addUser(bufferUsers)
    if selectedUsers.isEmpty then
      println(s"You aborted chat creation.")
      Option.empty[Chat]
    else if selectedUsers.size == 1 && selectedUsers.head.login == me.login then
      println(s"There is no users to add.")
      println(s"Type #add to try again or any other key to return to menu.")
      print(s"> ")
      val entry = readLine()
      if entry == "#add" then createChat()
      else Option.empty[Chat] // do nothing, simply return to menu
    else
      selectChatName(selectedUsers)




  @tailrec
  private def addUser(buffer: ListBuffer[User]): List[User] =
    println(s"Find users to add to chat, or type #end to finish adding.")
    print("> ")
    val user = readLine()
    if  user == "#end" && buffer.isEmpty then
      buffer.distinct.toList
    else if user == "#end" then
      me :: buffer.distinct.toList
    else if user == me.login then
      println(s"You do not need add manually yourself to chat.")
      addUser(buffer)
    else
      ExternalDB.findUser(user) match {
        case Left(queryErrors: QueryErrors) =>
          queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
          addUser(buffer)
        case Right(user: User) =>
          buffer.addOne(user)
          addUser(buffer)
      }




  @tailrec
  private def selectChatName(users: List[User]): Option[Chat] =
    println(s"Please select name for chat or type #end to escape chat creation.")
    print("> ")
    val name = readLine()
    if name == "#end" then Option.empty[Chat]
    else if ChatNameValidator.isValid(name) then
      ExternalDB.createChat(users, name) match {
        case Left(queryErrors: QueryErrors) =>
          println(s"Opppss, Cannot create new chat. See Errors below:")
          queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
          Option.empty[Chat]
        case Right(chat) =>
          // if chat is created correctly in DB,
          // we can create proper kafka topic for this chat
          KessengerAdmin.createNewChat(chat) match {
            case Left(kafkaError: KafkaError) =>
              println(s"Cannot create new chat. ${kafkaError.description}")
              Option.empty[Chat]
            case Right(chatt: Chat) =>
              // if we created proper kafka topic for chat
              // we send information obout to chat's participants
              manager.sendInvitations(chatt, users) match {
                case Left(kafkaError: KafkaError) =>
                  print(s"Chat created, but cannot send invitations to all selected users. See Error below...")
                  print(s"${kafkaError.description},\n")
                  // here chat exists only in db but if someone
                  // log in and read in his own chats,
                  // this chat will show up to him
                  Some(chatt)
                case Right((returnedChat, returnedList)) => Some(returnedChat)
              }
          }
      }
    else
      println(s"Chat name '$name' is invalid.\nMay contain only letters, numbers, whitespaces and underscores.")
      selectChatName(users)




  /**
   * Note -- this method is blocking because,
   * we need confirmation from DB of removal
   * proper records.
   *
   * @param chat chat we want to escape
   */
  private def escapeChat(chat: Chat): Unit =
    print(s"Escaping chat, please wait...\n> ")
    ExternalDB.deleteMeFromChat(me, chat) match {
      case Left(qErrors: QueryErrors) =>
        qErrors.listOfErrors.foreach(qe => print(s"${qe.description}\n> "))
        println(s"Cannot escape chat. Please try again later.")
      case Right(chatUsers: Int)      =>
        manager.sendMessage(chat, s"## ${me.login} Stopped participating in chat. ##")
        manager.escapeChat(chat)
        print(s"You escaped chat '${chat.chatName}'.\n> ")
        // we do not delete chat topic because
        // it is useful for some analytics
    }




  /**
   * In this method we can change our password
   * and login (if is not taken).
   */
  private def showSettings(): Unit =
    println(s"This functionality will be implemented in further versions.")




  /*
  *
  * Creating Account
  *
  */


  @tailrec
  private def createAccount(): Unit =
    print(s"Set your login. Login can contain letters and numbers.\n")
    print("Login cannot contain punctuation characters: !#%&'()*+,-./:;<=>?@[\\]^_`{|}~.\"$\n> ")
    val login = readLine()
    val loginRegex = "([\\p{Alnum}]*[\\p{Punct}]+[\\p{Alnum}]*)|([0-9]+)".r
    if login == "#exit"          then () // back to main menu
    else if loginRegex.matches(login) then
      println(s"Login is incorrect, try with another one, or type #exit.")
      createAccount()
    else
      setPassword(login, "")




  /**
   * @param login
   * @param s
   */
  @tailrec
  private def setPassword(login: Login, s: String): Unit =
    val console: Console = System.console()
    if console != null then
      print("Set your Password:\n> ")
      val pass1 = console.readPassword()
      if pass1 != null then
        println("Please repeat the password:")
        print("> ")
        val pass2 = console.readPassword()
        if pass2 != null && pass1.toSeq == pass2.toSeq && pass1.nonEmpty then
          // probably must call gc() to remove pass1 and pass2 from heap
          print("Passwords matched.\n> ")
          val salt = PasswordConverter.generateSalt
          PasswordConverter.convert(pass2.mkString(""), salt) match {
            case Left(_) =>
              print("Undefined error, try again\n> ")
              setPassword(login, s)
            case Right(p) =>
              ExternalDB.createUser(login, p, salt) match {
                case Left(QueryErrors(List(QueryError(queryErrorType, description)))) =>
                  // we print notification about error
                  // and return to main menu
                  print(s"Cannot create user account. See error below.\n> ")
                  print(s"$description\n> ")
                  // and return to login menu
                  runProgram(Array.empty)
                case Right(user: User) =>
                  // when user is created we need start Kessenger Admin for them.
                  KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
                  // And initialize it.
                  MyAccount.initialize(user) match {
                    // if initialization returns error we should print it
                    // and start program at the beginning
                    case Left((dbError, kafkaError)) =>
                      (dbError, kafkaError) match {
                        case (Some(db), None)      =>
                          // print error message
                          print(s"Cannot load chats from Database. See Error below...")
                          db.listOfErrors.foreach(error => print(s"${error.description}\n> "))
                          // and return to login menu
                          runProgram(Array.empty)
                        case (None, Some(kaf))     =>
                          // print error cannot connect to kafka broker
                          print(s"Cannot connect to chat server. See Error below...")
                          print(s"${kaf.description}\n> ")
                          // and return to login menu
                          runProgram(Array.empty)
                        case (Some(db), Some(kaf)) =>
                          // case not reachable
                          db.listOfErrors.foreach(error => print(s"${error.description}\n> "))
                          print(s"${kaf.description}\n> ")
                          runProgram(Array.empty)
                        case _                     =>
                          // case not reachable
                          print(s"Undefined Error\n> ")
                          runProgram(Array.empty)
                      }
                    case Right(chatManager: ChatManager) =>
                      // if we properly created joining topic
                      // we save user object
                      me = user.copy(salt = None)
                      // and save its chat manager
                      manager = chatManager
                      print(s"Account created correctly. Please Sign in now.\n")
                      // and finally shows users menu.
                      printMenu()
                  }
                case _ =>
                  print("Undefined error.\n> ")
                  setPassword(login, s)
              }
          }
        else
          print("Passwords do not match. Try Again.\n> ")
          setPassword(login, s)
      else
        print("Password was empty. Try with non empty.\n> ")
        setPassword(login, s)
    else
      print("Cannot read user input. Program termination.\n> ")

  end setPassword




  /**
   *
   */
  private def logout(): Unit =
    if manager != null then manager.closeChatManager()




  /**
   *
   */
  private def closeProgram(): Unit =
    if KessengerAdmin.getStatus == Running then
      Future { KessengerAdmin.closeAdmin() }
    ExternalDB.closeConnection() match {
      case Success(_) =>
        print(s"Disconnected with DB.\n")
      case Failure(_) =>
        print(s"Error with disconnecting with DB.\n")
    }
    executeHook.set(false)


end ProgramExecutor
