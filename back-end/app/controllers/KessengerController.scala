package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import components.actions.{SessionChecker, SessionUpdater}
import components.actors.WebSocketActor
import components.db.MyDbExecutor
import components.executioncontexts.{DatabaseExecutionContext, MyExecutionContext}
import components.util.converters.{JsonParsers, PasswordConverter, SessionConverter}
import io.github.malyszaryczlowiek.kessengerlibrary.db.queries.{DataProcessingError, LoginTaken, QueryError, UndefinedError, UnsupportedOperation}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{ChatId, DbResponse}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.{Chat, Domain, Settings, User}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Chat.parseChatToJSON
import io.github.malyszaryczlowiek.kessengerlibrary.domain.User.parseListOfUsersToJSON
import play.api.db.Database
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import util.HeadersParser

import scala.util.Random
import java.nio.charset.Charset
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




  /**
   * Create new Account
   *
   * @return
   */
//  @deprecated("old method")
//  def signup2 = Action.async { implicit request =>
//    if (!request.session.isEmpty) {
//      Future.successful(BadRequest("Logout from current Session and try again. "))
//    }
//    else {
//      fc.signupForm.bindFromRequest().fold(
//        formWithErrors => {
//          // binding failure, you retrieve the form containing errors:
//          val errors = formWithErrors.errors
//          val e = errors.foldLeft[String]("")((errors, error) => s"$errors\n${error.message}").trim
//          Future.successful(BadRequest(e).withNewSession) // .discardingCookies(DiscardingCookie("sid"))
//        },
//        loginCredentials => {
//          Future {
//            db.withConnection(implicit connection => {
//              val userId = UUID.randomUUID()
//              val login = loginCredentials.login
//              passwordConverter.convert(loginCredentials.pass) match {
//                case Left(_) =>
//                  InternalServerError("Error 001. Encoding password failed").withNewSession
//                case Right(encoded) =>
//                  val settings = Settings()
//                  val validityTime = System.currentTimeMillis() / 1000L + settings.sessionDuration // in seconds+ 900
//                  val sessionData = SessionInfo(UUID.randomUUID(), userId, validityTime)
//                  dbExecutor.createUser(User( userId, login), encoded, settings, sessionData) match {
//                    case Left(queryError) =>
//                      InternalServerError(s"Error 002. ${queryError.description.toString()}").withNewSession
//                    case Right(value) =>
//                      if (value == 3) {
//                        val session = new Session(
//                          Map(
//                            "session_id"    -> s"${sessionData.sessionId.toString}",
//                            "user_id"       -> s"${userId.toString}",
//                            "validity_time" -> s"$validityTime"
//                          )
//                        )
//                        Ok(userId.toString).withSession(session)
//                      } else InternalServerError("Error 003. User Creation Error. ").withNewSession
//                  }
//              }
//            })
//          }(databaseExecutionContext)
//        }
//      )
//    }
//  }





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
                    val userId = UUID.randomUUID()
                    passwordConverter.convert(loginCredentials.pass) match {
                      case Left(_) =>
                        Future.successful(
                          InternalServerError("Error 005. Encoding password failed")
                        )
                      case Right( encodedPass ) =>
                        val settings = Settings()
                        val user = User(userId, login)
                        Future {
                          db.withConnection(implicit connection => {
                            dbExecutor.createUser(user, encodedPass, settings, sessionData) match {
                              case Left(queryError) =>
                                InternalServerError(s"Error 006. ${queryError.description.toString()}")
                              case Right(value) =>
                                if (value == 3) Ok(jsonParser.toJSON((user, settings)))
                                else InternalServerError("Error 007. User Creation Error. ")
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

  //signin

  //  private val logForm = Form(
  //    mapping(
  //      "login" -> nonEmptyText.verifying(loginCheckConstraint),
  //      "pass" -> nonEmptyText.verifying(passwordCheckConstraint)
  //    )(LoginCredentials.apply)(LoginCredentials.unapply)
  //  )


  //  def signin2 = Action.async(implicit request => {
  //    fc.logForm.bindFromRequest().fold( // ()(request, formBinding)
  //      formWithErrors => {
  //        // binding failure, you retrieve the form containing errors:
  //        val errors = formWithErrors.errors
  //        val e = errors.foldLeft[String]("")((errors, error) => s"$errors\n${error.message}").trim
  //        Future.successful(BadRequest(s"Error 004. Form validation Error(s):\n$e").withNewSession)
  //      },
  //      loginCredentials => {
  //        // jeśli czas jest zgodny to trzeba sprawdzić czy login odpowiadający userId w sessji
  //        // jest taki sam jak login do którego ktoś chce się zalogować.
  //        Future {
  //          val hash = passwordConverter.convert(loginCredentials.pass) match {
  //            case Left(ex) => ex
  //            case Right(p) => p
  //          }
  //          db.withConnection(implicit connection => {
  //            dbExecutor.findUser(loginCredentials.login, hash) match {
  //              case Left(_) => BadRequest("Error 005. Login or Password not match.").withNewSession
  //              case Right((user,settings)) =>
  //                val sessionId = UUID.randomUUID()
  //                val validityTime = System.currentTimeMillis() / 1000L + settings.sessionDuration
  //                val session = new Session(
  //                  Map(
  //                    "session_id"    -> s"${sessionId.toString}",
  //                    "user_id"       -> s"${user.userId.toString}",
  //                    "validity_time" -> s"$validityTime"
  //                  )
  //                )
  //                dbExecutor.createSession(sessionId, user.userId, validityTime) match {
  //                  case Left(_) =>
  //                    InternalServerError("Error 006. Cannot Create session in DB.").withNewSession
  //                  case Right(v) =>
  //                    dbExecutor.removeAllExpiredUserSessions(user.userId)
  //                    if (v == 1) {
  //                      Redirect(routes.KessengerController.user(user.userId))
  //                        .withSession(session)
  //                        .withHeaders(("Internal", "true"))
  //                    } else InternalServerError("Error 007. Not matching row affected.").withNewSession
  //                }
  //            }
  //          })
  //        }(ec)
  //      }
  //    )
  //  })


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
                              case Left(_) => BadRequest("Error 013. Login or Password not match.")
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
                    if (v == 1) Ok("Logout successfully.")
                    else Accepted("Not matching session.")
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
        jsonParser.parseSettings(s.toString()) match {
          case Left(_) => Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. "))
          case Right(settings) =>
            Future {
              db.withConnection( implicit connection => {
                dbExecutor.updateSettings(userId, settings) match {
                  case Left(_) => InternalServerError(s"Error XXX")
                  case Right(v) =>
                    if (v == 1) Ok(s"New Settings Saved.")
                    else Accepted(s"Nothing to updated.") // something bad
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    })


  def changeMyLogin(userId: UUID) =
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
                  case LoginTaken => BadRequest("Error 029. Login taken. Try with another one. ")
                  case _ => InternalServerError("Error 030.")
                }
              case Right(i) =>
                if (i == 1) Ok(newLogin)
                else Accepted("Warning 031. Nothing to do.")
            }
          })
        }(databaseExecutionContext)
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    })



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
                    dbExecutor.updateUsersPassword(userId,old, neww) match {
                      case Left(_) => InternalServerError(s"Error XXX")
                      case Right(v) =>
                        if (v == 1) Ok(s"New Password Saved.")
                        else Accepted(s"Nothing to updated.") // something bad
                    }
                  })
                }(databaseExecutionContext)
              case _ => Future.successful(InternalServerError(s"Error XXX. Conversion Error."))
            }
        }
    }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))


//  jsonParser.parseSettings(s.toString()) match {
//          case Left(_) => Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. "))
//          case Right(settings) =>
//            Future {
//              db.withConnection(implicit connection => {
//                dbExecutor.updateSettings(userId, settings) match {
//                  case Left(_) => InternalServerError(s"Error XXX")
//                  case Right(v) =>
//                    if (v == 1) Ok(s"New Settings Saved.")
//                    else Accepted(s"Nothing to updated.") // something bad
//                }
//              })
//            }(databaseExecutionContext)
//        }
//      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))

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
              else Ok(jsonParser.toJSON(found))
          }
        })
      }(databaseExecutionContext)
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
            case Right(listOfUser) => Ok(jsonParser.toJSON(listOfUser))
          }
        })
      }(databaseExecutionContext)
    })




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
              Ok(s"W czacie zostało $value użytkowników.")
          }
        }
      }(databaseExecutionContext)
    )






  def updateChatName(userId: UUID, chatId: String) =
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
      request.body.asText.map(newName => {
        Future {
          db.withConnection { implicit connection =>
            dbExecutor.updateChatName(userId, chatId, newName) match {
              case Left(queryError) =>
                InternalServerError(s"Error 026. ${queryError.description.toString()}")
              case Right(value) =>
                if (value == 1) Ok("name changed")
                else BadRequest("Error 027, Cannot change chat name. User is not participant of chat or chat does not exist.")
            }
          }
        }(databaseExecutionContext)
      }).getOrElse(Future.successful(InternalServerError(s"Error 028. Cannot parse payload data. ")))
    )






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
                  case Right(value)     => Ok(s"$value users added.")
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 020. Cannot parse JSON data.")))
      )






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
              db.withConnection( implicit connection => {
                dbExecutor.createChat(me, users, chatName) match {
                  case Left(QueryError(_, UnsupportedOperation)) => BadRequest("Error 012. Chat already exists.")
                  case Left(queryError) => InternalServerError(s"Error 013. ${queryError.description.toString()}")
                  case Right(createdChat) => Ok(parseChatToJSON(createdChat))
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(BadRequest("Error 014, JSON parsing error.")))
    })





  // jak serwer wyśle odpowiedź, że czat został zapisany w bazie danych
  // to należy we frontendzie wysłać na kafkę powiadomienia o dadaniu do czatu.
//  @deprecated
//  def newGroupChat(userId: UUID) =
//    SessionChecker(parse.anyContent, userId)(
//      databaseExecutionContext,
//      db,
//      dbExecutor,
//      headersParser
//    )
//      .andThen(
//        SessionUpdater(parse.anyContent, userId)(
//          databaseExecutionContext,
//          db,
//          dbExecutor,
//          headersParser
//        )
//      )
//      .async(implicit request => {
//        request.body.asJson.map(json => {
//          jsonParser.newGroupChatJSON(json.toString()) match {
//            case Left(_) => Future.successful(BadRequest("Error 015, JSON parsing error.").withSession(sessionConverter.updateSession(request)))
//            case Right((users, chatName)) =>
//              Future {
//                db.withConnection(implicit connection => {
//                  val chatId = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())
//                  dbExecutor.createGroupChat(users, chatName, chatId) match {
//                    case Left(_) => InternalServerError("Error 016. Database Error").withSession(sessionConverter.updateSession(request))
//                    case Right(chat: Chat) =>
//                      Ok(parseChatToJSON(chat)).withSession(sessionConverter.updateSession(request))
//                  }
//                })
//              }(databaseExecutionContext)
//          }
//        })
//          .getOrElse(Future.successful(BadRequest("Error 017, JSON parsing error.").withSession(sessionConverter.updateSession(request))))
//      })


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
            if (value == "localhost:4200") {
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
                    if (value > 0 ) {
                      println(s"odebrałem rządanie HTTP.")
                      Right(
                        ActorFlow.actorRef { out =>
                          println(s"wszedłem w ActorFlow.")
                          WebSocketActor.props(out)
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




  @deprecated
  def ws =
    WebSocket.accept[String, String] { request =>

      println()
      println(s"websocket HEADERS")
      request.headers.headers.foreach(println)

      // Headers.create().add("Access-Control-Allow-Origin" -> "http://localhost:4200").get("Access-Control-Allow-Origin") match {


      //WebSocket.acceptOrResult[String, String] { request =>
      println(s"odebrałem rządanie HTTP.")
      ActorFlow.actorRef { out =>
        println(s"wszedłem w ActorFlow.")
        WebSocketActor.props(out)
      }
    }







  /*
  zbudować 4 endpointy
    1. GET zwracający obiekt User
    2. POST wysyłający dane do logowania z tokenem CSRF.
    3. POST obsługujący CSRF wysyłający w body stringa
    4. websocket gdzie wysyłając get na odpowiedni endpoint uruchamiamy aktora i tak dalej
    5. soprawdzić czy obsługiwane będzie LazyList
   */


  def angular() = Action.async { implicit request =>
    val headers = request.headers.headers

    println()
    println()
    if (request.session.isEmpty) println(s"Sesja jest pusta")
    else {
      request.session.data.foreach(println)
    }


    headers.foreach(println)

    val cookies = request.cookies.toList
    cookies.foreach(println)

    val u1 = User(UUID.randomUUID(), "user1")
    val u2 = User(UUID.randomUUID(), "user2")

    val users = List(u1,u2)

    val session = new Session(
      Map(
        "session_id" -> s"${UUID.randomUUID().toString}",
        "user_id" -> s"${UUID.randomUUID().toString}",
        "validity_time" -> s"${Random.nextLong()}"
      )
    )
    val cookie = new Cookie("KESSENGER_SID", "wartosc-sid", httpOnly = false, domain = Option("localhost:4200")  ) // , sameSite = Option(Cookie.SameSite.Lax))
    request.headers.get("MY_KESSENGER_HEADER") match {
      case Some(value) =>
        Future.successful(Ok(jsonParser.toJSON(users)).withSession(session).withCookies(cookie).withHeaders(("MY_KESSENGER_HEADER", value)))
      case None => Future.successful(Ok(jsonParser.toJSON(users)).withSession(session).withCookies(cookie))
    }

    // Future.successful(Ok(jsonParser.toJSON(users)).withSession(session).withCookies(cookie))
  }


  def userStreaming = Action.async { implicit request =>
    val u1 = User(UUID.randomUUID(), "user1")
    val u2 = User(UUID.randomUUID(), "user2")
    val u3 = User(UUID.randomUUID(), "user3")
    lazy val list = u1 #:: u2 #:: u3 #:: LazyList.empty
    // lazy val source = Source.apply(list.toList.map(parseUserToJSON))
    val session = new Session(
      Map(
        "session_id" -> s"${UUID.randomUUID()}",
        "user_id" -> s"${UUID.randomUUID()}",
        "validity_time" -> s"${System.currentTimeMillis()}"
      )
    )
    Future.successful(Ok(parseListOfUsersToJSON(list.toList)).withSession(session))
  }

  def angularpost = Action.async { implicit request =>
    request.body.asText
    println("przetwarzam post")
    Future.successful(Ok("przerobiono rządanie."))
  }




  // TODO ważne -> zawsze należy przy końcowym wysyłaniu response dodać      .withSession( sessionConverter.updateSession(request) )
  //  żeby zawsze spisał wartość nowej daty ważności z header'a




  def json = Action.async { implicit request =>
    val u = User(UUID.randomUUID(), "Marik")
    Future.successful(Ok(jsonParser.toJSON(u)))
  }

  def jsonpost = Action.async { implicit request =>
    request.body.asJson.map(jsv => {
      val str = jsv.toString()
      println()
      println(s"posted user $str")
      println()
      jsonParser.parseUser(str) match {
        case Left(_) => Future.successful(BadRequest("Cannot parse JSON payload."))
        case Right(u) => Future.successful(Ok(u.toString))
      }
    }).getOrElse(Future.successful(NotAcceptable("Sorry Buddy. ")))
  }


  def jsonarray = Action.async { implicit request =>
    val u1 = User(UUID.randomUUID(), "1")
    val u2 = User(UUID.randomUUID(), "2")
    Future.successful(
      Ok(jsonParser.toJSON(List(u1, u2)))
    )
  }

  // post to get user back
  def jsonarraypost = Action.async { implicit request =>
    request.body.asJson.map(jsv => {
      jsonParser.parseListOfUsers(jsv.toString()) match {
        case Left(_) => Future.successful(BadRequest("Cannot parse JSON payload."))
        case Right(u) => Future.successful(Ok(u.toString))
      }
    }).getOrElse(Future.successful(NotAcceptable("Sorry Buddy. ")))
  }


  def usersNewSession = Action {

    val byteArray =  "zakodowana wiadomość".getBytes(Charset.defaultCharset())
    Ok(byteArray).withNewSession
  }


}


