package com.github.malyszaryczlowiek
package account

import db.ExternalDB
import messages.{ChatManager, KessengerAdmin}

import kessengerlibrary.db.queries.{QueryError, QueryErrorMessage, QueryErrorType, QueryErrors}
import kessengerlibrary.domain.{Chat, User}
import kessengerlibrary.kafka.errors.{KafkaError, KafkaErrorMessage}

import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import concurrent.ExecutionContext.Implicits.global



object MyAccount:

  /**
   *
   */
  def initialize(me: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
    val chatManager = new ChatManager(me)
    if me.joiningOffset == -1 then
      chatManager.tryToCreateJoiningTopic() match {
        // here we handle problem when joining topic exists,
        // but we could not update joining offset in db earlier
        case lke @ Left( KafkaError(_, KafkaErrorMessage.ChatExistsError) ) =>
          chatManager.updateOffset(0L)
          // so if user had joining offset > than -1 its means that he could have
          // some chats. So we search them
          findUsersChats(me, chatManager)
        case Left(kafkaError: KafkaError) =>
          // in case of other kafka errors we cannot use
          // chat manager
          chatManager.closeChatManager()
          // and we return obtained kafka error
          Left(None, Option(kafkaError))
        case Right(_) =>
          // in case when we created joining topic correctly
          // we need to update users offset in db
          // and in Chat Manager
          chatManager.updateOffset(0L)
          chatManager.startListeningInvitations()
          Right (chatManager)
      }
    else
      findUsersChats(me, chatManager)



  /**
   *
   * @param me
   * @param chatManager
   * @return
   */
  private def findUsersChats(me: User, chatManager: ChatManager): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
    ExternalDB.findUsersChats(me) match {
      case Left(dbError: QueryErrors) =>

        // because we do not use chatManager
        // we close it
        chatManager.closeChatManager()

        // and return obtained error message
        Left(Some(dbError), None)

      case Right(usersChats: Map[Chat, List[User]]) =>

        // we add all saved in db chats to chat manager
        chatManager.addChats(usersChats)

        // and start listening from kafka broker
        chatManager.startListeningInvitations()

        // we start all MessagePrinters to collect new incoming messages
        chatManager.startAllChats()

        // and return chatManager object finally
        Right(chatManager)
    }



























//  private def checkKafkaError(kafkaError: KafkaError): Unit =
//    kafkaError match {
//      case ke @ KafkaError(_, KafkaErrorMessage.ChatExistsError) => // here we handle problem when joining topic exists but we could not update joining offset in db earlier
//        updateOffset()
//
//
////        ExternalDB.updateJoiningOffset(me, 0L) match {
////          case Right(user) =>
////            //println(s"User's data updated. ")      //  delete after tests
////            //println(s"offset ${me.joiningOffset}") //  delete after tests
////            chatManager.updateOffset( me.joiningOffset )
////            chatManager.setTopicCreated( true )
////            tryToStartChatManager(chatManager) // if offset is updated we try to restart listener in chatManager
////          case Left(dbError: QueryErrors) =>
////            // this error isn't problem because we automatically handle it
////            // when running up next time
////            // println(s"Cannot update user's joining offset: ${dbError.listOfErrors.head.description}")
////            Left(Some(dbError), Some(ke))
////        }
//      case _ =>
//        // in case of other kafka error, we simply return it
//        chatManager.closeChatManager()
//        Left(None, ske)
//    }


  /**
   * this method comparing to initialize() avoids,
   * sending request to DB.
   */
//  def initializeAfterCreation(user: User): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
//    me = user
//    val chatManager = new ChatManager(me, false)
//    tryToStartChatManager(chatManager)



//  @tailrec
//  private def tryToStartChatManager(chatManager: ChatManager): Either[(Option[QueryErrors], Option[KafkaError]), ChatManager] =
//    chatManager.getError() match {
//      case ske @ Some() =>
//
//      case None =>
//        // if chat manager started normally we try to update user's joining offset in DB
//        //updateOffset(chatManager, None)
//        Right(chatManager) // chat manager created without any internal errors
//    }
//

//  def updateUser(user: User): Unit =
//    me = user

  // def getMyObject: User = me
  // def getMyChats: immutable.SortedMap[Chat, ChatExecutor] = myChats.to(immutable.SortedMap)

  // def getChatExecutor(chat: Chat): Option[ChatExecutor] = getMyChats.get(chat)


//  def addChat(chat: Chat, users: List[User]): Unit =
//    myChats.addOne((chat, new ChatExecutor(me, chat, users)))
//
//  def updateChat(updatedChat: Chat, chatExecutor: ChatExecutor): Unit  =
//    // note, old chatExecutor is the same class object as new chat executor.
//    myChats.find( (oldChat: Chat, oldExecutor: ChatExecutor) => oldChat.chatId == updatedChat.chatId) match {
//      case None               => // nothing to do
//      case Some((oldChat, _)) =>
//        myChats.remove(oldChat)
//        myChats.addOne(updatedChat -> chatExecutor)
//    }
//
//
//
//  def removeChat(chatToRemove: Chat): Unit =
//    myChats.find( (chat: Chat, oldExecutor: ChatExecutor) => chat.chatId == chatToRemove.chatId) match {
//      case None               =>
//        print(s"Cannot remove chat '${chatToRemove.chatName}'. Does not exist in chat list.\n> ")
//      case Some((found, _)) =>
//        myChats.remove(found)
//        print(s"Chat '${found.chatName}' removed from list.\n> ")
//    }


  /**
   *
   * @return returns Sequence of updated chats,
   *         which must be saved to DB subsequently.
   */
//  def logOut(): Unit =
//    // we close all kafka connections
//    myChats.values.par.map(_.closeChat())
////    val chatsToSave = myChats.values.par.map(_.closeChat()).seq.toSeq       // close all Kafka connections
////    ExternalDB.updateChatOffsetAndMessageTime(me, chatsToSave) match {
////      case Left(queryErrors: QueryErrors) =>
////        println(s"LOGOUT DB ERROR.") //  delete it
////        println(s"${queryErrors.listOfErrors.head.description}")
////      case Right(value) =>
////        println(s"Updated $value chats to DB.")
////    }
//
//    // and make my chats empty
//    val keys = myChats.keys
//    keys.foreach(chat => myChats.remove(chat))
//    // myChats.empty                               // make chat map empty
//    me = User(UUID.randomUUID(), "NULL_LOGIN")  // reassign user to null one