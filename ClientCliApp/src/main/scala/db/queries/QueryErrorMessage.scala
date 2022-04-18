package com.github.malyszaryczlowiek
package db.queries

import domain.User

import com.github.malyszaryczlowiek.domain.Domain.Login

enum QueryErrorMessage(message: String):
  override def toString: String = message

  case UserNotFound(login: Login)        extends QueryErrorMessage(s"$login Not Found.")
  case NoDbConnection                    extends QueryErrorMessage("Connection to DB lost. Try again later.")
  case DataProcessingError               extends QueryErrorMessage("Data Processing Error.")
  case UndefinedError(e: String = "")    extends QueryErrorMessage(s"Undefined Error. $e")
  case LoginTaken                        extends QueryErrorMessage("Sorry Login is taken, try with another one.")
  case AtLeastTwoUsers                   extends QueryErrorMessage("To create new chat, you have to select two users at least.")
  case SomeUsersNotAddedToChat           extends QueryErrorMessage("Some users not added to chat.")
  case CannotAddUserToChat(login: Login) extends QueryErrorMessage(s"Cannot add user $login to chat.")
  case TryingToAddNonExistingUser        extends QueryErrorMessage(s"You trying to add non existing user.")
  // case UserIsParticipantOfChat