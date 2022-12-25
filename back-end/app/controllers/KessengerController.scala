package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import components.actions.{SessionChecker, SessionUpdater}
import components.actors.WebSocketActor
import components.db.MyDbExecutor
import components.executioncontexts.{DatabaseExecutionContext, MyExecutionContext}
import components.util.converters.{JsonParsers, PasswordConverter, SessionConverter}

import io.github.malyszaryczlowiek.kessengerlibrary.db.queries.{DataProcessingError, LoginTaken, QueryError, UndefinedError, UnsupportedOperation}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Chat, Settings, User, ResponseBody}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Settings.parseJSONtoSettings
import io.github.malyszaryczlowiek.kessengerlibrary.model.Chat.parseJSONtoChat
import io.github.malyszaryczlowiek.kessengerlibrary.model.User.toJSON

import play.api.db.Database
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import util.HeadersParser

import java.util.UUID
import javax.inject._
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future}




class KessengerController @Inject()
  (
    val controllerComponents: ControllerComponents,
    val db: Database,
    val dbExecutor: MyDbExecutor,
    val passwordConverter: PasswordConverter,
    val sessionConverter: SessionConverter,
    val jsonParser: JsonParsers,
    val headersParser: HeadersParser,
    // val fc: FormsAndConstraint,
    // implicit val futures: Futures, // do async
    implicit val databaseExecutionContext: DatabaseExecutionContext,
    implicit val ec: MyExecutionContext,
    implicit val system: ActorSystem,
    mat: Materializer
  ) extends BaseController {



  // TODO write validator for json data login length and so one
  def signup = Action.async { implicit request =>
    request.headers.get("KSID") match {
      case Some( ksid ) =>
        headersParser.parseKSID(ksid) match {
          case Some( sessionData ) =>
            request.body.asJson.map(json => jsonParser.parseCredentials(json.toString())) match {
              case Some( parsedJSONbody )  =>
                parsedJSONbody match {
                  case Left(_) => Future.successful(BadRequest("Error 004. Cannot parse JSON payload."))
                  case Right( loginCredentials ) =>
                    val login = loginCredentials.login
                    val userId = UUID.randomUUID() // here we create another userId
                    passwordConverter.convert(loginCredentials.pass) match {
                      case Left(_) =>
                        Future.successful(
                          InternalServerError("Error 005. Encoding password failed")
                        )
                      case Right( encodedPass ) =>
                        val settings = Settings(sessionDuration = 900000L) // todo change in kessenger-lib from 900L to 900000L
                        val user = User(userId, login)
                        Future {
                          db.withConnection(implicit connection => {
                            dbExecutor.createUser(user, encodedPass, settings, sessionData) match {
                              case Left(queryError) =>
                                InternalServerError(s"Error 006. ${queryError.description.toString()}")
                              case Right(value) =>
                                if (value == 3) Ok(jsonParser.toJSON((user, settings)))
                                else InternalServerError(ResponseBody(7, "Error 007. User Creation Error. ").toString)
                            }
                          })
                        }(databaseExecutionContext)
                    }
                } // no payload
              case None => Future.successful(BadRequest("Error 010. No payload."))
            } // invalid KSID header
          case None => Future.successful(Unauthorized("Error 009. Invalid Request."))
        } // no KSID header
      case None => Future.successful(Unauthorized("Error 008. Try to reload page. "))
    }
  }



  // TODO this works
  /**
   * sprawdź login i hasło jeśli pasują w db to należy
   * @return
   */
  def signin = Action.async { implicit request =>
    request.headers.get("KSID") match {
      case Some(ksid) =>
        headersParser.parseKSID(ksid) match {
          case Some(sessionData) =>
            request.body.asJson.map(json => jsonParser.parseCredentials(json.toString())) match {
              case Some( parsedJSONbody ) =>
                parsedJSONbody match {
                  case Left(_) => Future.successful(BadRequest("Error 011. Cannot parse JSON payload."))
                  case Right(loginCredentials) =>
                    passwordConverter.convert(loginCredentials.pass) match {
                      case Left(_) => Future.successful( InternalServerError("Error 012. Encoding password failed") )
                      case Right(encodedPass) =>
                        Future {
                          db.withConnection(implicit connection => {
                            dbExecutor.findUser(loginCredentials.login, encodedPass) match {
                              case Left(_) => BadRequest(ResponseBody(13, "Login or Password not match.").toString)
                              case Right((user, settings)) =>
                                val validityTime = System.currentTimeMillis() + settings.sessionDuration
                                dbExecutor.createSession(sessionData.sessionId, user.userId, validityTime) match {
                                  case Left(_) =>
                                    InternalServerError("Error 014. Cannot Create session in DB.")
                                  case Right(v) =>
                                    dbExecutor.removeAllExpiredUserSessions(user.userId, System.currentTimeMillis())
                                    if (v == 1) Ok(jsonParser.toJSON((user, settings)))
                                    else InternalServerError("Error 015. Not matching row affected.")
                                }
                            }
                          })
                        }(databaseExecutionContext)
                    }
                } // no payload
              case None => Future.successful(BadRequest("Error 010. No payload."))
            } // invalid KSID header
          case None => Future.successful(Unauthorized("Error 009. Invalid Request."))
        } // no KSID header
      case None => Future.successful(Unauthorized("Error 008. Try to reload page. "))
    }
  }




  def logout = Action.async { implicit request =>
    request.headers.get("KSID") match {
      case Some(ksid) =>
        headersParser.parseKSID(ksid) match {
          case Some(sessionData) =>
            Future {
              db.withConnection(implicit connection => {
                dbExecutor.removeSession(sessionData.sessionId, sessionData.userId) match {
                  case Left(_) => InternalServerError("Error 018. Db Error. ")
                  case Right(v) =>
                    if (v == 1) Ok(ResponseBody(0, "Logout successfully.").toString)
                    else Accepted(ResponseBody(0, "Not matching session.").toString)
                }
              })
            }(databaseExecutionContext)
          // invalid KSID header
          case None => Future.successful(Unauthorized("Error 017. Invalid Request."))
        } // no KSID header
      case None => Future.successful(Unauthorized("Error 016. Try to reload page. "))
    }
  }




  def user(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      Future {
        db.withConnection(implicit connection => {
          dbExecutor.findUserWithUUID(userId) match {
            case Left(value) => InternalServerError("Error 009. You are logged in with SessionChecker and SessionUpdater ")
            case Right(userAndSettings) => Ok(jsonParser.toJSON(userAndSettings))
          }
        })
      }(databaseExecutionContext)
    })




  // todo works
  def changeSettings(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async( implicit request => {
      request.body.asJson.map( s => {
        parseJSONtoSettings(s.toString()) match {
          case Left(_) => Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. "))
          case Right(settings) =>
            Future {
              db.withConnection( implicit connection => {
                dbExecutor.updateSettings(userId, settings) match {
                  case Left(_) => InternalServerError(s"Error XXX")
                  case Right(v) =>
                    if (v == 1) Ok(ResponseBody(0, s"New Settings Saved.").toString)
                    else Accepted(s"Nothing to updated.") // something bad
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    })




  // todo works
  def changeLogin(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      request.body.asText.map(newLogin => {
        Future {
          db.withConnection(implicit connection => {
            dbExecutor.updateMyLogin(userId, newLogin) match {
              case Left(QueryError(_, m)) =>
                m match {
                  case LoginTaken => BadRequest(ResponseBody(29, "Login taken. Try with another one.").toString  )
                  case _ => InternalServerError("Error 030.")
                }
              case Right(i) =>
                if (i == 1) Ok(ResponseBody(0, "Login successfully changed!!!").toString)
                else BadRequest(ResponseBody(31, "Oppsss, User not found???").toString)
            }
          })
        }(databaseExecutionContext)
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    })





  // todo works
  def changePassword(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      request.body.asJson.map( json => {
        jsonParser.parseNewPass(json.toString()) match {
          case Left(_) => Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. "))
          case Right((oldP, newP)) =>
            val (o,n) = (
              passwordConverter.convert(oldP),
              passwordConverter.convert(newP)
            )
            (o,n) match {
              case (Right(old), Right(neww)) =>
                Future {
                  db.withConnection(implicit connection => {
                    dbExecutor.updateUsersPassword(userId, old, neww) match {
                      case Left(_) => InternalServerError(s"Error XXX")
                      case Right(v) =>
                        if (v == 1) Ok(ResponseBody(0,s"New Password Saved.").toString)
                        else BadRequest(ResponseBody(22,  s"Old Password does not match.").toString)
                    }
                  })
                }(databaseExecutionContext)
              case _ => Future.successful(InternalServerError(s"Error XXX. Conversion Error."))
            }
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    })



  def searchUser(userId: UUID, u: String) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      Future {
        db.withConnection(implicit connection => {
          dbExecutor.findUser(u) match {
            case Left(_) =>
              InternalServerError("Error 010.")
            case Right(found) =>
              if (found.isEmpty) NoContent
              else Ok(toJSON(found))
          }
        })
      }(databaseExecutionContext)
    })


  // tworzenie pojedyńczego czatu
  // jak metoda zwróci wynik to należy
  // we front endzie wysłać info do kafki do wszystkich użytkowników
  // tod powininec zwracać informacje o chatcie.
  def newChat(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      request.body.asJson.map(json => {
        jsonParser.parseNewChat(json.toString()) match {
          case Left(_) => Future.successful(BadRequest("Error 011. Cannot parse JSON payload."))
          case Right((me, users, chatName)) =>
            Future {
              db.withConnection(implicit connection => {
                dbExecutor.createChat(me, users, chatName) match {
                  case Left(QueryError(_, UnsupportedOperation)) => BadRequest(ResponseBody(12, "Chat already exists.").toString  )
                  case Left(queryError) => InternalServerError(s"Error 013. ${queryError.description.toString()}")
                  case Right(createdChat) =>
                    // todo tutaj utworzyć jeszcze chat w kafce. i dopiero jak otrzymamy potwierdzenie
                    //  z  kafki można wysłać odpiwiedź, że czat został utworzony.


                    Ok(jsonParser.chatsToJSON(createdChat))
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(BadRequest("Error 014, JSON parsing error.")))
    })



  /**
   *
   * @return returns user's chats
   */

  def getChats(userId: UUID) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      // tutaj dodać pobieranie danych o wszystkich czatach w któych jest użytkownik
      // następnie musi (powinno?) nastąpić przekierowanie na endpoint web socketa
      // odpowiedziealny za komunikację z serwerem
      Future {
        db.withConnection(implicit connection => {
          dbExecutor.findMyChats(userId) match {
            case Left(value) => InternalServerError("Error 009. You are logged in with SessionChecker and SessionUpdater ")
            case Right(chats) => Ok( jsonParser.chatsToJSON(chats) )
          }
          })
      }(databaseExecutionContext)
    })






  def getChatUsers(userId: UUID, chatId: ChatId) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request => {
      Future {
        db.withConnection( implicit connection => {
          dbExecutor.findChatUsers(chatId) match {
            case Left(_) => InternalServerError("Error 021. Database Error")
            case Right(listOfUser) => Ok(toJSON(listOfUser))
          }
        })
      }(databaseExecutionContext)
    })




  // todo check this method
  def leaveChat(userId: UUID, chatId: String) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request =>
      Future {
        db.withConnection { implicit connection =>
          dbExecutor.leaveTheChat(userId, chatId, groupChat = true) match {
            case Left(QueryError(_, UnsupportedOperation)) =>
              BadRequest("Error 022. You cannot leave this type of chat.")
            case Left(QueryError(_, DataProcessingError)) =>
              InternalServerError(s"Error 023. ${DataProcessingError.toString()}")
            case Left(_) =>
              InternalServerError(s"Error 024. ${UndefinedError.toString()}")
            case Right(value) =>
              Ok
          }
        }
      }(databaseExecutionContext)
    )






  def setChatSettings(userId: UUID, chatId: String) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request =>
      request.body.asJson.map(payload => {
        parseJSONtoChat(payload.toString()) match {
          case Left(_) =>
            Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. "))
          case Right(chat: Chat) =>
            Future {
              db.withConnection { implicit connection =>
                dbExecutor.updateChat(userId, chat) match {
                  case Left(queryError) =>
                    InternalServerError(s"Error 026. ${queryError.description.toString()}")
                  case Right(value) =>
                    if (value == 1) Ok(ResponseBody(0, "Settings saved").toString)
                    else BadRequest(ResponseBody(27, "Cannot change chat settings. ").toString)
                    // User is not participant of chat or chat does not exist.
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    )






  // todo not tested
  def addUsersToChat(userId: UUID, chatId: ChatId) =
    SessionChecker(parse.anyContent, userId)(
      databaseExecutionContext,
      db,
      dbExecutor,
      headersParser
    ).andThen(
      SessionUpdater(parse.anyContent, userId)(
        databaseExecutionContext,
        db,
        dbExecutor,
        headersParser
      )
    ).async(implicit request =>
      request.body.asJson.map(newChatUsers => {
        jsonParser.parseNewChatUsers(newChatUsers.toString()) match {
          case Left(_) => Future.successful(BadRequest("Error 018, JSON parsing error."))
          case Right((chatName, users)) =>
            Future {
              db.withConnection { implicit connection =>
                dbExecutor.addNewUsersToChat(users, chatId, chatName) match {
                  case Left(queryError) => InternalServerError(s"Error 019. ${queryError.description.toString()}")
                  case Right(value)     => Ok(ResponseBody(0,s"$value users added.").toString)
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 020. Cannot parse JSON data.")))
      )








  // TODO tutaj należy sprawdzić header (nagłówek) Origin z rządania
  //  jeśli będzie zgody z localhost:4200 to nie odrzucać.
  //  oraz sprawdzić czy dany userId ma w bazie danych ważną sesję
  //  jeśli tak to uruchomić Actora
  //  jeśli nie to odrzućić


  // dokończyć definiowanie websocketa
  //  https://www.playframework.com/documentation/2.8.x/ScalaWebSockets
  def ws(userId: UUID) =
    WebSocket.acceptOrResult[String, String] { request =>
      Future.successful(
        request.headers.get("Origin") match {
          case Some(value) =>
            println("Rządanie ma Origin header")
            if (value == "http://localhost:4200") {  // todo change hardcoded origin value
              val f = Future {
                db.withConnection( implicit connection => {
                  dbExecutor.getNumOfValidUserSessions(userId)
                })
              }(databaseExecutionContext)
              try {
                val result = Await.result(f, Duration.create(2L, SECONDS))
                result match {
                  case Left(_) => Left(InternalServerError("Error XXX."))
                  case Right(value) =>
                    // this means we have one valid session at leased
                    println(s"Rządanie ma $value ważnych sesji.")
                    if (value > 0 ) {
                      Right(
                        ActorFlow.actorRef { out =>
                          println(s"wszedłem w ActorFlow.")
                          WebSocketActor.props(out, jsonParser, databaseExecutionContext)
                        }
                      )
                    } else Left(Unauthorized("Error XXX. No valid session."))
                }
              } catch {
                case e: Throwable => Left(InternalServerError("Error XXX."))
              }
            } else Left(BadRequest("Error XXX."))
          case None => Left(BadRequest("Error XXX."))
        }
      )
  }



}


