package com.github.malyszaryczlowiek
package programExecution

import java.io.Console
import scala.util.{Failure, Success, Try}
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.io.StdIn.{readChar, readInt, readLine}
import com.github.malyszaryczlowiek.account.MyAccount
import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, Password}
import com.github.malyszaryczlowiek.domain.User
import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaProductionConfigurator
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorType}
import com.github.malyszaryczlowiek.messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import com.github.malyszaryczlowiek.util.{ChatNameValidator, PasswordConverter}




object ProgramExecutor :

  private var manager: Option[ChatManager] = None


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
          if      value == 1 then signIn()
          else if value == 2 then createAccount()
          else if value == 3 then () // exit method and program
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
                signInWithLogin(login)
              case Right(ep) => checkCredentials(login, ep, salt)
            }
          case Left(queryErrors: QueryErrors) =>
            val mes = queryErrors.listOfErrors.head.description
            println(mes)
        }
      else
        println("Some error occurred, try again.")
        signInWithLogin(login)
    else
      println("OOOps problem with Console. Cannot use it. Program Terminates.")



  private def checkCredentials(login: Login, password: Password, salt: String): Unit =
    ExternalDB.findUser(login, password, salt) match {
      case Left(QueryErrors(l @ List(QueryError(queryErrorType, description)))) =>
        println(s"$description")
        signIn()
      case Right(user) => // user found
        MyAccount.initialize(user) match {

          // todo tutaj zmienić tak aby obsługiwał wszystkie przypadki







          case Left(errorsTuple) =>
            errorsTuple match {
              case (Some(dbError), None)     =>
                println(s"${dbError.listOfErrors.head.description}")
                printMenu()
              case (None, Some(kafkaError))  =>

                // TODO
//                kafkaError match {
//                  case KafkaError(_, KafkaErrorMessage.ChatExistsError) => // here we handle problem when joining topic exists but we cannot update joining offset in db
//                    ExternalDB.updateJoiningOffset(user, 0L) match {
//                      case Right(user) =>
//                        MyAccount.updateUser(user)
//                        println(s"Users data updated. ")
//                        printMenu()
//                      case Left(dbError: QueryErrors) =>
//                        println(s"${dbError.listOfErrors.head.description}")
//                        printMenu()
//                    }
//                  case _ =>
//                    println(s"${kafkaError.description}")
//                    printMenu()
//                }
              case (Some(dbEr), Some(kafEr)) =>
                // TODO implement
              case (None, None)              => () // Not reachable
            }
          case Right(_) => printMenu()
        }
      case _ =>  // not reachable
        println("Other problem.")
        signIn()
    }




  @tailrec
  private def printMenu(): Unit =
    println(s"Menu:")
    println(s"1) Show my chats.")
    println(s"2) Create new chat")
    println(s"3) Settings.")
    println(s"4) Log out and Exit.")
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
          createChat()  // TODO  implement
          printMenu()
        else if value == 3 then
          showSettings() // TODO  implement
          printMenu()
        else if value == 4 then
          MyAccount.logOut()
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
            println(s"Oppsss, your input does not match integer number between 1 and ${indexed.length} or '#back'")
              //s"or #escape_chat if you do not want participate")
            showChats()
          }



  /**
   * this method must start kafka producer and consumer
   */
  private def workInChat(chat: Chat, executor: ChatExecutor): Unit =
    if chat.groupChat then
      println(s"You are in ${chat.chatName}, type '#back' to return to chat list\n " +
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
  @tailrec
  private def createChat(): Option[Chat] =
    val bufferUsers   = ListBuffer.empty[User]
    val selectedUsers = addUser(bufferUsers)
    if selectedUsers.isEmpty then
      println(s"There is no users to add.")
      println(s"Type #add to try again or any other key to return to menu.")
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
      buffer.distinct.filter(u => u != MyAccount.getMyObject).toList
    else
      ExternalDB.findUser(user) match {
        case Left(queryErrors: QueryErrors) =>
          println(s"${queryErrors.listOfErrors.head.description}")
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
          println(s"${queryErrors.listOfErrors.head.description}")
          Option.empty[Chat]
        case Right(chat) =>
          ChatManager.askToJoinChat(users, chat)  // TODO zmień na klasę chat managera

          // TODO send invitations to other
          // TODO send first message with information of chat creation
          None


      }
    else
      println(s"Chat name '$name' is invalid.\nMay contain only letters, numbers, whitespaces and underscores.")
      selectChatName(users)




  
  private def escapeChat(executor: ChatExecutor): Unit =
    val me = executor.getUser
    ExternalDB.deleteMeFromChat( me, executor.getChat) match {
      case Left(queryErrors: QueryErrors) =>
        queryErrors.listOfErrors.foreach(qe => println(qe.description))
        println(s"Try again later.")
      case Right(chat: Chat)                    =>
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
  private def showSettings(): Unit = ???


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
    if login == "#exit"          then ()
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
          // probably must call gc() to remove pass1 and pass2
          println("Passwords matched.")
          val salt = PasswordConverter.generateSalt
          PasswordConverter.convert(pass2.mkString(""), salt) match {
            case Left(value) =>
              println("Undefined error, try again")
              setPassword(login, s)
            case Right(p) =>
              ExternalDB.createUser(login, p, salt) match {
                case Left(QueryErrors(List(QueryError(queryErrorType, description)))) =>
                  println(s"Error: $description")
                  println(s"You are moved back to User Creator.")
                  createAccount()
                case Right(user: User) =>
                  MyAccount.initializeAfterCreation(user) match {   // TODo tutaj zmienić pattern matching






                    case Left(kafkaError) =>
                      println(s"System Error: ${kafkaError.description}.")
                      println(s"You cannot get invitations to chat from other users.")
                      println(s"Try to log in in a few minutes.")
                      printMenu()
                    case Right(user) =>
                      ExternalDB.updateJoiningOffset(user, 0L) match {
                        case Right(offset)                  => printMenu() // offset updated in db
                        case Left(queryErrors: QueryErrors) =>
                          println(s"Error: ${queryErrors.listOfErrors.head.description}")



                      }
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
