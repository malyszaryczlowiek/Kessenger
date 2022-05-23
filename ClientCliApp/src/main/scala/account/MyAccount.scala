package com.github.malyszaryczlowiek
package account

import com.github.malyszaryczlowiek.db.ExternalDB
import com.github.malyszaryczlowiek.db.queries.{QueryError, QueryErrorMessage, QueryErrorType, QueryErrors}
import com.github.malyszaryczlowiek.domain.Domain.{Login, UserID}
import com.github.malyszaryczlowiek.domain.User
import messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import messages.ChatGivens.given

import com.github.malyszaryczlowiek.messages.kafkaConfiguration.KafkaProductionConfigurator
import com.github.malyszaryczlowiek.messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorType, KafkaErrorsHandler}

import java.util.UUID
import scala.collection.immutable.SortedMap
import scala.collection.{immutable, mutable}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.parallel.mutable.ParTrieMap
import collection.parallel.CollectionConverters.IterableIsParallelizable
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object MyAccount:

  private val myChats: ParTrieMap[Chat, ChatExecutor] = ParTrieMap.empty[Chat, ChatExecutor]
  private var me: User = _


  /**
   *
   * @param user
   */
  def initialize(user: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
    KessengerAdmin.startAdmin(new KafkaProductionConfigurator)
    me = user
    if user.joiningOffset == -1 then
      val chatManager = new ChatManager(me, false)
      tryToStartChatManager(chatManager)
    else
      ExternalDB.findUsersChats(user) match {
        case Left(dbError: QueryErrors) => Left(Some(dbError), None)
        case Right(usersChats: Map[Chat, List[User]]) =>
          val transform = usersChats.map(
            (chatList: (Chat, List[User])) =>
              (chatList._1, new ChatExecutor(me, chatList._1, chatList._2))
          )
          myChats.addAll(transform)
          val chatManager = new ChatManager(me, false)
          chatManager.startListening() match {
            case ke @ Some(_) => Left((None, ke))
            case None         => Right(chatManager)
          }
      }



  /**
   * this method comparing to initialize() avoids,
   * sending request to DB.
   * @param user
   */
  def initializeAfterCreation(user: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
    me = user
    val chatManager = new ChatManager(me, false)
    tryToStartChatManager(chatManager)



  @tailrec
  private def tryToStartChatManager(chatManager: ChatManager): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
    chatManager.startListening() match {
      case ske @ Some(kafkaError: KafkaError) =>
        kafkaError match {
          case ke @ KafkaError(_, KafkaErrorMessage.ChatExistsError) => // here we handle problem when joining topic exists but we cannot update joining offset in db
            ExternalDB.updateJoiningOffset(me, 0L) match {
              case Right(user) =>
                me = user
                println(s"Users data updated. ")
                chatManager.updateOffset(me.joiningOffset)
                tryToStartChatManager(chatManager) // if offset is updated we try to restart listener in chatManager
              case Left(dbError: QueryErrors) =>
                println(s"${dbError.listOfErrors.head.description}")
                Left(Some(dbError), Some(ke))
            }
          case _ => Left(None, ske) // in case of other kafka error, we simply return it
        }
      case None => Right(chatManager) // chat manager created without any internal errors
      }



  // TODO wziąć to w try i utworzyć egzemplarz klasy, jak nie wywali błędu to zwrócić
  // go jako Right, ale najpierw updejtować offset.
  // jak wywali błąd to zwrócić ten błąd


  def updateUser(user: User): Unit = me = user


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