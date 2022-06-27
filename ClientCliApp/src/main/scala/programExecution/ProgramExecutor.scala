package com.github.malyszaryczlowiek
package programExecution

import java.io.Console
import scala.util.{Failure, Success, Try}
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.io.StdIn.{readChar, readInt, readLine}
import account.MyAccount
import db.ExternalDB
import db.queries.{QueryError, QueryErrorMessage, QueryErrors}
import domain.Domain.{Login, Password}
import domain.User
import messages.kafkaConfiguration.KafkaProductionConfigurator
import messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorStatus}
import messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import util.{ChatNameValidator, PasswordConverter}

import scala.::




object ProgramExecutor :

  private var manager: Option[ChatManager] = None
  //private var myAccount: Option[MyAccount] = None


  @tailrec
  def runProgram(args: Array[String]): Unit =
    val length = args.length
    if length == 0 then
      println("Select what to do:\n1) Sign in,\n2) Create Account,\n3) Exit.")
      print("> ")
      Try { readInt() } match {
        case Failure(exception) =>
          println("Please select 1, 2 or 3.")
          runProgram(args)
        case Success(value) =>
          if   value == 1    then
            signIn()
            runProgram(Array.empty)
          else if value == 2 then
            createAccount()
            runProgram(Array.empty)
          else if value == 3 then
            ExternalDB.closeConnection()
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
          ExternalDB.findUsersSalt(login) match {
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



  private def checkCredentials(login: Login, password: Password, salt: String): Unit =
    ExternalDB.findUser(login, password, salt) match {
      case Left(queryErrors: QueryErrors) =>
        queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
        signIn()
      case Right(user) => // user found
        // from test purposes, better is to move starting KafkaAdmin out of MyAccount object
        // TODO **********************************************************************
        KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
        MyAccount.initialize(user) match {
          case Left(errorsTuple) =>
            errorsTuple match {
              case (Some(dbError), None)     =>
                println(s"${dbError.listOfErrors.head.description}")
                printMenu()
              case (None, Some(kafkaError))  =>
                println(s"${kafkaError.description}")
                printMenu()
              case (Some(dbEr), Some(kafEr)) =>
                println(s"${dbEr.listOfErrors.head.description}")
                println(s"${kafEr.description}")
              case (None, None)              =>
                printMenu() // Not reachable
            }
          case Right(chatManager: ChatManager) =>
            manager = Some(chatManager)
            printMenu()
        }
    }




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
          MyAccount.logOut()
          KessengerAdmin.closeAdmin()
        else
          println("Wrong number, please select 1, 2, 3 or 4.")
          printMenu()
    }



  @tailrec
  private def showChats(): Unit =
    val chats: immutable.SortedMap[Chat, ChatExecutor] = MyAccount.getMyChats
    val chatSize = chats.size
    if chats.isEmpty then println(s"Ooopsss, you have not chats.")  // and we return to menu
    else
      println(s"Please select chat, or type #back to menu.")
      println("Your chats:")
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
            println(s"Oppsss, your input does not match integer number between 1 and ${indexed.length} or '#back'")
              //s"or #escape_chat if you do not want participate")
            showChats()
          }



  /**
   * this method must start kafka producer and consumer
   */
  private def workInChat(chat: Chat, executor: ChatExecutor): Unit =
    if chat.groupChat then
      println(s"You are in \'${chat.chatName}\' chat, type your messages, or type '#back' to return to chat list\nor '#escape_chat if you do not want participate longer in group chat. ")
    else
      println(s"You are in '${chat.chatName}' chat, type your messages, or type '#back' to return to chat list.")
    executor.printUnreadMessages()
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
      executor.stopPrintMessages()
      executor
    } match {
      case Failure(exception)    =>
        println(s"Unexpected Error in chat.")
      case Success(chatExecutor: ChatExecutor) =>
        val chat = chatExecutor.getChat
        val me = MyAccount.getMyObject
        MyAccount.updateChat(chat, chatExecutor)
        // we save offset to db
        ExternalDB.updateChatOffsetAndMessageTime(me, Seq(chat)) match {
          case Left(qe: QueryErrors) =>
            println(s"Cannot update chat information:  ")
            qe.listOfErrors.foreach(error => println(s"${error.description}"))
          case Right(saved: Int)     =>
            // we do not need notify user about updates in DB
        }
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
    if selectedUsers.size == 1 && selectedUsers.head.login == MyAccount.getMyObject.login then
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
    if user == "#end" then
      val me = MyAccount.getMyObject
      me :: buffer.distinct.toList
    else if user == MyAccount.getMyObject.login then
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
      manager match {
        case Some(chatManager: ChatManager) =>
          ExternalDB.createChat(users, name) match {
            case Left(queryErrors: QueryErrors) =>
              queryErrors.listOfErrors.foreach(error => println(s"${error.description}"))
              Option.empty[Chat]
            case Right(chat) =>
              KessengerAdmin.createNewChat(chat) match {
                case Left(kafkaError: KafkaError) =>
                  println(s"Cannot create new chat. ${kafkaError.description}")
                  Option.empty[Chat]
                case Right(chatt: Chat) =>
                  chatManager.askToJoinChat(users, chatt) match {
                    case Left(kafkaError: KafkaError) =>
                      println(s"${kafkaError.description}, Cannot send invitations to other users... ")
                      Option.empty[Chat]
                    case Right(createdChat) =>
                      println(s"Chat '${createdChat.chatName}' created correctly.'")
                      Some(createdChat)
                  }
              }
          }
        case None =>
          println(s"Kafka Connection Error, please try to log in again in a while.")
          Option.empty[Chat]
      }
    else
      println(s"Chat name '$name' is invalid.\nMay contain only letters, numbers, whitespaces and underscores.")
      selectChatName(users)





  private def escapeChat(executor: ChatExecutor): Unit =
    val me = executor.getUser
    ExternalDB.deleteMeFromChat( me, executor.getChat) match {
      case Left(qErrors: QueryErrors) =>
        qErrors.listOfErrors.foreach(qe => println(qe.description))
        println(s"Try again later.")
      case Right(chat: Chat)          =>
        executor.sendMessage(s"## ${me.login} Stopped participating in chat. ##")
        executor.closeChat()
        println(s"You escaped chat ${chat.chatName}.")
        MyAccount.removeChat(chat) match {
          case Some(_) => println(s"Chat ${chat.chatName} removed from list.")
          case None    => println(s"Oooopsss, chat you want to escape and delete not found. ")
        }
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
    println("Login must have 8 characters at leased and must contain only letters, numbers or underscore '_'. If you want exit type #exit:")
    print("> ")
    val login = readLine()
    val regex = "[\\w]{8,}".r
    if login == "#exit"          then () // back to main menu
    else if regex.matches(login) then setPassword(login, "")
    else
      println(s"Login is incorrect, try with another one, or type #exit.")
      createAccount()



  @tailrec
  private def setPassword(login: Login, s: String): Unit =
    val console: Console = System.console()
    if console != null then
      println("Set your Password:")
      print("> ")
      val pass1 = console.readPassword()
      if pass1 != null then
        println("Please repeat the password:")
        print("> ")
        val pass2 = console.readPassword()
        if pass2 != null && pass1.toSeq == pass2.toSeq then
          // probably must call gc() to remove pass1 and pass2 from heap
          println("Passwords matched.")
          val salt = PasswordConverter.generateSalt
          PasswordConverter.convert(pass2.mkString(""), salt) match {
            case Left(_) =>
              println("Undefined error, try again")
              setPassword(login, s)
            case Right(p) =>
              ExternalDB.createUser(login, p, salt) match {
                case Left(QueryErrors(List(QueryError(queryErrorType, description)))) =>
                  println(s"Error: $description")
                  println(s"You are moved back to User Creator.")
                  createAccount()
                case Right(user: User) =>
                  // from test purposes, better is to move starting KafkaAdmin out of MyAccount object
                  KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
                  MyAccount.initializeAfterCreation(user) match {
                    case Left((dbError, kafkaError)) =>
                      (dbError, kafkaError) match {
                        case (Some(db), None)      => println(s"${db.listOfErrors.head.description}")
                        case (None, Some(kaf))     => println(s"${kaf.description}")
                        case (Some(db), Some(kaf)) =>
                          println(s"${db.listOfErrors.head.description}")
                          println(s"${kaf.description}")
                        case _                     => println(s"Undefined Error") // not reachable
                      }
                    case Right(chatManager: ChatManager) => manager = Some(chatManager) // and we return to menu
                  }
                case _ =>
                  println("Undefined error.")
                  setPassword(login, s)
              }
          }
        else
          println("Passwords do not match. Try Again.")
          setPassword(login, s)
      else
        println("Password was empty. Try with non empty.")
        setPassword(login, s)
    else
      println("Cannot read user input. Program termination.")
