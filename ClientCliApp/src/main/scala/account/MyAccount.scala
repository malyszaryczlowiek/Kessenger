package com.github.malyszaryczlowiek
package account

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrorMessage, QueryErrorType, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.User
import messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import messages.ChatGivens.given

import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorsHandler}

import java.util.UUID
import scala.collection.immutable.SortedMap
import scala.collection.{immutable, mutable}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.parallel.mutable.ParTrieMap
import collection.parallel.CollectionConverters.IterableIsParallelizable
import scala.util.{Failure, Success, Try}

object MyAccount:

  private val myChats: ParTrieMap[Chat, ChatExecutor] = ParTrieMap.empty[Chat, ChatExecutor]
  private var me: User = _


  /*
  TODO zrobić tak by przy inicjalizacji sprawdzał czy joining chat istnieje tzn
    sprawdzał czy user.offset jest >-1 jeśli tak to niecch uruchamia
    Chatmanagera normalnie natomiast jeśli ma -1 niech spróbuje jeszcze raz utworzyć
    jak się uda to niech zmieni od razu offset w DB na 0.
    jak się nie uda to musi wyświetlić komunikat, ż enie może przyjmować zaproszeń
    i żeby spróbować uruchomić ponownie aplikację za jakiś czas.
  */

  /**
   *
   * @param user
   */
  def initialize(user: User): Either[(Option[QueryErrors], Option[KafkaError]), User] =
    me = user


    // ###################################################
    sprawdzić czy offset jest -1
    jeśli tak to spróbowac utowrzyć joina
      -- jeśli się uda to
       1. updejtować offset w db
       2. wczytać usersChats

     -- jeśli się nie uda to wyświetlić komunikat i wczytać usersChats

       podobnie zrobić  w initializeAfterCreation


       i wszysstko poprawić w ProgramExecutor.

       // ###################################################


    ExternalDB.findUsersChats(user) match {
      case Left(dbError: QueryErrors) =>
        //println(s"Cannot initialize user's chats. Query Error (${dbError.listOfErrors.head.description}). ")
        Try { ChatManager.startChatManager() } match {
          case Success(_)  => Left((Some(dbError), None))
          case Failure(ex) =>
            KafkaErrorsHandler.handle(ex) match {
              case Left(kafkaError: KafkaError) => Left((Some(dbError), Some(kafkaError)))
            }
        }
      case Right(usersChats: Map[Chat, List[User]]) =>
        // println(s"Users chats (${usersChats.size}) loaded. ")
        val transform = usersChats.map( (chatList: (Chat, List[User])) => (chatList._1, new ChatExecutor(me, chatList._1, chatList._2)))
        myChats.addAll(transform)
        Try { ChatManager.startChatManager() } match {
          case Success(_)  => Right(user)
          case Failure(ex) =>
            KafkaErrorsHandler.handle(ex) match {
              case Left(kafkaError: KafkaError) => Left((None, Some(kafkaError)))
            }
        }
      case _ =>
        val undefined = QueryErrors(List(QueryError(QueryErrorType.ERROR, QueryErrorMessage.UndefinedError())))
        Try { ChatManager.startChatManager() } match {
          case Success(_)  => Left((Some(undefined), None))
          case Failure(ex) =>
            KafkaErrorsHandler.handle(ex) match {
              case Left(kafkaError: KafkaError) => Left((Some(undefined), Some(kafkaError)))
            }
        }
    }



  /**
   * this method comparing to initialize() avoids,
   * sending request to DB.
   * @param user
   */
  def initializeAfterCreation(user: User): Either[KafkaError, Unit] =
    me = user
    KessengerAdmin.createJoiningTopic(user.userId) match {
      case l @ Left(kafkaError) => l // in this case we cannot create our joining topic so we need to retry later
      case Right(_) =>
        ChatManager.startChatManager()

        // TODO ERROR naprawić
        ERROR


    }



  def getMyObject: User = me
  def getMyChats: immutable.SortedMap[Chat, ChatExecutor] = myChats.to(immutable.SortedMap)


  /**
   *
   * @return returns Sequence of updated chats,
   *         which must be saved to DB subsequently.
   */
  def logOut(): Unit =
    val chatsToSave = myChats.values.par.map(_.closeChat()).seq.toSeq       // close all Kafka connections
    ExternalDB.updateChatOffsetAndMessageTime(me, chatsToSave) match {
      case Left(queryErrors: QueryErrors) =>
        println(s"${queryErrors.listOfErrors.head.description}")
      case Right(value) =>
        println(s"Updated $value chats to DB.")
    }
    myChats.empty                               // make chat map empty
    me = User(UUID.randomUUID(), "NULL_LOGIN")  // reassign user to null one



  def addChat(chat: Chat, users: List[User]): Unit =
    myChats.addOne((chat, new ChatExecutor(me, chat, users)))



  def removeChat(chat: Chat): Option[ChatExecutor] = myChats.remove(chat)