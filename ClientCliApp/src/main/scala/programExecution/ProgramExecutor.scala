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
import com.github.malyszaryczlowiek.messages.ChatManager
import com.github.malyszaryczlowiek.util.PasswordConverter


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
    println(s"Menu:")
    println(s"1) Show my chats.")
    println(s"2) Create new chat")
    println(s"3) Exit.")
    Try {
      readInt()
    } match {
      case Failure(exception) => println("Please select 1, 2 or 3.")
      case Success(value) =>
        if value == 1 then
          showChats()  // TODO implement
        else if value == 2 then
          createChat()  // TODO  implement
        else if value == 3 then
          continueMainLoop = false
        else
        println("Wrong number, please select 1, 2 or 3.")
        printMenu()
    }

  private def showChats(): Unit = ???
  private def createChat(): Unit = ???



/*
TODO list
1) implement ExternalDB.findUsersChatsMap()
2) implement MyAccount.initialize()
3) implement showChats()
4) implement createChat()

*/






