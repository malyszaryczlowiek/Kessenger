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
import kessengerlibrary.kafka.errors.KafkaError
import kessengerlibrary.status.Status
import kessengerlibrary.status.Status.{Closing, NotInitialized, Running, Starting, Terminated}

import java.io.Console
import scala.::
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.io.StdIn.{readChar, readInt, readLine}
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global


object ProgramExecutor :

  private var manager: ChatManager = _
  private var me: User = _


  @tailrec
  def runProgram(args: Array[String]): Unit =

    // if we want to force close program we should close all connections
    // ass well
    // Runtime.getRuntime.addShutdownHook(new Thread(new Runnable() { override def run(): Unit = { logout() } }))
    Runtime.getRuntime.addShutdownHook(new Thread(() => logout()))
    val length = args.length
    if length == 0 then
      println(s"Kessenger v0.1.0")
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
            runProgram(Array.empty)
          else if value == 3 then
            ExternalDB.closeConnection() match {
              case Success(_) =>
                print(s"Disconnected with DB.\n")
              case Failure(_) =>
                print(s"Error with disconnecting with DB.\n")
            }
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
          ExternalDB.findUsersSalt(login) match { // TODO to powoduje problem jak nie ma na samym początku connection
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
        me = user.copy(salt = None)
        KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
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


//  private def initializeUser(me: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
//    if me.joiningOffset == -1 then
//      val chatManager = new ChatManager(me, false)
//      tryToStartChatManager(chatManager)
//    else
//      ExternalDB.findUsersChats(user) match {
//        case Left(dbError: QueryErrors)               =>
//          Left(Some(dbError), None)
//        case Right(usersChats: Map[Chat, List[User]]) =>
//          val transform = usersChats.map(
//            (chatList: (Chat, List[User])) =>
//              val chat = chatList._1
//              val users = chatList._2
//              (chat, new ChatExecutor(me, chat, users))
//          )
//          myChats.addAll(transform)
//          val chatManager = new ChatManager(me, true)
//          chatManager.getError match {
//            case ke @ Some(_) =>
//              // if something goes wrong we should close chat manager
//              chatManager.closeChatManager()
//              Left((None, ke))
//            case None         => Right(chatManager)
//          }
//      }


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

          // TODO when testing take care of closing everything
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
        chatIndex => {
          // if offset is different than 0 means that user read from chat so accept them
          // TODO reimplement it @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
          if chatIndex._1.offset == 0L then
            println(s"${chatIndex._2 + 1}) ${chatIndex._1.chatName} (NOT ACCEPTED)")
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
          print(s"You are in \'${chat.chatName}\' chat, type your messages, or type '#back' to return to chat list\n"
            + "or '#escape_chat if you do not want participate longer in group chat.\n> ")
        else
          print(s"You are in '${chat.chatName}' chat, type your messages, or type '#back' to return to chat list.\n> ")

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
            print(s"Status messagePrintera po zamknięciu chatu $status\n> ")
        }
      case None =>
        {} // not reachable
    }

    /*

    case Failure(exception)    =>

          case Success(chatExecutor: ChatExecutor) =>
            val chat = chatExecutor.getChat
            val me = MyAccount.getMyObject
            MyAccount.updateChat(chat, chatExecutor)
            // we save offset to db
            ExternalDB.updateChatOffsetAndMessageTime(me, Seq(chat)) match {
              case Left(qe: QueryErrors) =>
                println(s"Cannot update chat information:  ")
                qe.listOfErrors.foreach(error => print(s"${error.description}\n> "))
              case Right(saved: Int)     =>
              // we do not need notify user about updates in DB
            }

    */





  /**
   * In this method we sent notification to other users,
   * who should participate the chat.
   *
   */
  @tailrec
  private def createChat(): Option[Chat] =
    val bufferUsers   = ListBuffer.empty[User]
    val selectedUsers = addUser(bufferUsers)
    if selectedUsers.size == 1 && selectedUsers.head.login == me.login then
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
                  print(s"${kafkaError.description},\n> ")
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





  private def escapeChat(chat: Chat): Unit =
    print(s"Escaping chat, please wait...\n> ")
    ExternalDB.deleteMeFromChat(me, chat) match {
      case Left(qErrors: QueryErrors) =>
        qErrors.listOfErrors.foreach(qe => print(s"${qe.description}\n> "))
        println(s"Cannot escape chat. Please try again later.")
      case Right(chatUsers: Int)      =>
        manager.sendMessage(chat, s"## ${me.login} Stopped participating in chat. ##")
        manager.escapeChat(chat)

        // TODO ******************************

        // TODO here continue reimpelementaiton

        // TODO ******************************

        val removedChat = executor.getChat
        MyAccount.removeChat( removedChat )
        print(s"You escaped chat '${removedChat.chatName}'.\n> ")
        if chatUsers == 0 then
          // if In chat is no more users we need remove topic from kafka Broker.
          Future {
            ExternalDB.deleteChat(removedChat) match {
              case Left(qErrors: QueryErrors) =>
                qErrors.listOfErrors.foreach(qe => print(s"${qe.description}\n> "))
                // println(s"Nie MOżnA USUnąć za bazy danych czatu. ") // DELETE
              case Right(chatUsers: Int) =>
                // print(s"$chatUsers czat usunięty z tablei chats") // DELETE
            }
          }
          Future {
            KessengerAdmin.removeChat(removedChat) match {
              case Left(ke: KafkaError)      =>
                // print(s"ERROR przy permanentnym usuwaniu topika czatu.\n> ") // DELETE
                // if we got error we do not inform user about
              case Right(permanentlyRemovedChat) =>
                print(s"Chat ${permanentlyRemovedChat.chatName} was removed permanently.\n> ")
            }
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
   * TODO write integration tests ???
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
                  print(s"Error: $description\n> ")
                  print(s"You are moved back to User Creator.\n> ")
                  createAccount()
                case Right(user: User) =>
                  // from test purposes, better is to move starting KafkaAdmin out of MyAccount object
                  KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
                  MyAccount.initializeAfterCreation(user) match {
                    case Left((dbError, kafkaError)) =>
                      (dbError, kafkaError) match {
                        case (Some(db), None)      => db.listOfErrors.foreach(error => print(s"${error.description}\n> "))
                        case (None, Some(kaf))     => print(s"${kaf.description}\n> ")
                        case (Some(db), Some(kaf)) =>
                          db.listOfErrors.foreach(error => print(s"${error.description}\n> "))
                          print(s"${kaf.description}\n> ")
                        case _                     => print(s"Undefined Error\n> ") // not reachable
                      }
                    case Right(chatManager: ChatManager) =>
                      // we assign chatManager and return to menu
                      manager = Some(chatManager)
                      print(s"Account created correctly. Please Sign in now.\n")
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



  private def logout(): Unit =
    manager match {
      case Some(chatManager: ChatManager) => chatManager.closeChatManager()
      case None =>
    }
    KessengerAdmin.closeAdmin()
    ExternalDB.closeConnection()
    manager = None
  end logout