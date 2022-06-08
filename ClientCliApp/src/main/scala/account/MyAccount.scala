package com.github.malyszaryczlowiek
package account

import db.ExternalDB
import db.queries.{QueryError, QueryErrorMessage, QueryErrorType, QueryErrors}
import domain.Domain.{Login, UserID}
import domain.User
import messages.{Chat, ChatExecutor, ChatManager, KessengerAdmin}
import messages.ChatGivens.given
import messages.kafkaConfiguration.KafkaProductionConfigurator
import messages.kafkaErrorsUtil.{KafkaError, KafkaErrorMessage, KafkaErrorStatus, KafkaErrorsHandler}

import java.util.UUID
import scala.collection.immutable.SortedMap
import scala.collection.{immutable, mutable}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.parallel.mutable.ParTrieMap
//import collection.parallel.CollectionConverters.IterableIsParallelizable
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}


class MyAccount:


//object MyAccount:

  private val myChats: ParTrieMap[Chat, ChatExecutor] = ParTrieMap.empty[Chat, ChatExecutor]
  private var me: User = _


  /**
   *
   * @param user
   */
  def initialize(user: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
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
          val chatManager = new ChatManager(me, true)
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
          case ke @ KafkaError(_, KafkaErrorMessage.ChatExistsError) => // here we handle problem when joining topic exists but we could not update joining offset in db earlier
            ExternalDB.updateJoiningOffset(me, 0L) match {
              case Right(user) =>
                me = user
                println(s"User's data updated. ")
                println(s"offset ${me.joiningOffset}")
                chatManager.updateOffset( me.joiningOffset )
                chatManager.setTopicCreated( true )
                tryToStartChatManager(chatManager) // if offset is updated we try to restart listener in chatManager
              case Left(dbError: QueryErrors) =>
                println(s"Cannot update user's joining offset: ${dbError.listOfErrors.head.description}")
                Left(Some(dbError), Some(ke))
            }
          case _ => Left(None, ske) // in case of other kafka error, we simply return it
        }
      case None =>
        // if chat manager started normally we try to update user's joining offset in DB
        //updateOffset(chatManager, None)
        Right(chatManager) // chat manager created without any internal errors
    }





  def updateUser(user: User): Unit =
    me = user

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