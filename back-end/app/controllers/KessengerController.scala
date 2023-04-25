package controllers

import akka.actor.ActorSystem
import ch.qos.logback.classic.Logger
import components.actions.{SessionChecker, SessionUpdater}
import components.actors.WebSocketActor
import components.db.MyDbExecutor
import components.executioncontexts.{DatabaseExecutionContext, KafkaExecutionContext}
import components.util.converters.{JsonParsers, PasswordConverter}
import conf.KafkaConf
import util.{HeadersParser, KafkaAdmin}
import io.github.malyszaryczlowiek.kessengerlibrary.db.queries.{DataProcessingError, LoginTaken, QueryError, UndefinedError, UnsupportedOperation}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{ChatId, UserID}
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Chat, Invitation, PartitionOffset, ResponseBody, Settings, User}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Settings.parseJSONtoSettings
import io.github.malyszaryczlowiek.kessengerlibrary.model.Chat.parseJSONtoChat
import io.github.malyszaryczlowiek.kessengerlibrary.model.User.toJSON
import io.github.malyszaryczlowiek.kessengerlibrary.model.UserOffsetUpdate.parseUserOffsetUpdate
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import java.util.UUID
import javax.inject._
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future}

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}


@Singleton
class KessengerController @Inject()
  (
    val controllerComponents: ControllerComponents,
    val db: Database,
    val dbExecutor: MyDbExecutor,
    val passwordConverter: PasswordConverter,
    val jsonParser: JsonParsers,
    val headersParser: HeadersParser,
    val databaseExecutionContext: DatabaseExecutionContext,
    val kafkaExecutionContext: KafkaExecutionContext,
    val kafkaAdmin: KafkaAdmin,
    @Named("KafkaConfiguration") implicit val configurator: KafkaConf,
    val lifecycle: ApplicationLifecycle,
    implicit val system: ActorSystem,
    //implicit val stringLoader: ConfigLoader[String]

    // val fc: FormsAndConstraint,
    // implicit val futures: Futures, // do async
    // implicit val ec: MyExecutionContext,
    // val sessionConverter: SessionConverter,
  ) extends BaseController {

//  val config = Configuration.load(Environment.simple(new File("./conf/application.conf"), Mode.Dev))
//  val foo = config.  getString("kessenger.kafka.broker.hosts").getOrElse("NIE MA")



  private val logger: Logger = LoggerFactory.getLogger(classOf[KessengerController]).asInstanceOf[Logger]



  lifecycle.addStopHook { () =>
    Future.successful(kafkaAdmin.closeAdmin())
  }



  // TODO write validator for json data login length and so one
  def signup: Action[AnyContent] = Action.async { implicit request =>
    request.headers.get("KSID") match {
      case Some( ksid ) =>
        headersParser.parseKSID(ksid) match {
          case Some( sessionData ) =>
            request.body.asJson.map(json => jsonParser.parseCredentials(json.toString())) match {
              case Some( parsedJSONbody )  =>
                parsedJSONbody match {
                  case Left(_) =>
                    logger.error(s"Cannot parse JSON payload.")
                    Future.successful(BadRequest(ResponseBody(1, "Bad Request.").toString))
                  case Right( loginCredentials ) =>
                    val login = loginCredentials.login
                    val userId = UUID.randomUUID() // here we create another userId
                    passwordConverter.convert(loginCredentials.pass) match {
                      case Left(_) =>
                        logger.error(s"Encoding password failed")
                        Future.successful(InternalServerError(ResponseBody(7, "Internal Server Error.").toString))
                      case Right( encodedPass ) =>
                        val settings = Settings(sessionDuration = 900000L)
                        // todo zmienić w kessenger-lib że nie ma wartości początkowej
                        //  a wartość tutaj przypisujemy z configuracji.
                        val user = User(userId, login)
                        Future {
                          db.withConnection(implicit connection => {
                            dbExecutor.createUser(user, encodedPass, settings, sessionData) match {
                              case Left(QueryError(_, LoginTaken)) =>
                                logger.trace(s"Login Taken. userId(${userId.toString})")
                                BadRequest(ResponseBody(6, LoginTaken.toString()).toString)
                              case Left(queryError: QueryError) =>
                                logger.error(s"Database Error: ${queryError.description.toString()}. userId(${userId.toString})")
                                InternalServerError(ResponseBody(7, queryError.description.toString()).toString )
                              case Right(value) =>
                                if (value == 3) {
                                  kafkaAdmin.createInvitationTopic(userId) match {
                                    case Left(_) =>
                                      logger.error(s"Kafka Error: Cannot create invitation topic. userId(${userId.toString})")
                                      dbExecutor.deleteUser(user.userId)
                                      InternalServerError(ResponseBody(7, "User Creation Error. Try again later").toString)
                                    case Right(_) =>
                                      logger.trace(s"User created successfully. userId(${userId.toString})")
                                      Ok(jsonParser.toJSON((user, settings)))
                                  }
                                }
                                else {
                                  logger.error(s"Cannot create new user. userId(${userId.toString})")
                                  InternalServerError(ResponseBody(7, "Error. Cannot create new user.").toString)
                                }

                            }
                          })
                        }(databaseExecutionContext)
                    }
                } // no payload
              case None =>
                logger.error(s"SignUp. Creating new user Error. No payload.")
                Future.successful(BadRequest(ResponseBody(10, s"Bad Request").toString))
            } // invalid KSID header
          case None =>
            logger.error(s"SignUp. Failed KSID header parsing.")
            Future.successful(Unauthorized(ResponseBody(9, s"Unauthorized").toString))
        } // no KSID header
      case None =>
        logger.error(s"SignUp. No KSID header.")
        Future.successful(Unauthorized(ResponseBody(8, s"Unauthorized").toString))
    }
  }





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
                  case Left(_) =>
                    logger.error(s"SignIn. Cannot parse payload.")
                    Future.successful(BadRequest(ResponseBody(11, s"Bad Request").toString()))
                  case Right(loginCredentials) =>
                    passwordConverter.convert(loginCredentials.pass) match {
                      case Left(_) =>
                        logger.error(s"SignIn. Encoding password failed. userId(${loginCredentials.userId})")
                        Future.successful(InternalServerError(ResponseBody(12, "Internal Server Error.").toString))
                      case Right(encodedPass) =>
                        Future {
                          db.withConnection(implicit connection => {
                            dbExecutor.findUser(loginCredentials.login, encodedPass) match {
                              case Left(QueryError(_, DataProcessingError)) =>
                                logger.error(s"SignIn. Database Processing Error. userId(${loginCredentials.userId})")
                                BadRequest(ResponseBody(13, "Login or Password not match.").toString)
                              case Left(qe) =>
                                logger.error(s"SignIn. Database Error: ${qe.description.toString()}. userId(${loginCredentials.userId})")
                                InternalServerError(ResponseBody(133, "Database Error.").toString)
                              case Right((user, settings, chatList)) =>
                                val validityTime = System.currentTimeMillis() + settings.sessionDuration
                                dbExecutor.createSession(sessionData.sessionId, user.userId, validityTime) match {
                                  case Left(_) =>
                                    logger.error(s"SignIn. Database Error. Cannot create user Session. userId(${loginCredentials.userId})")
                                    InternalServerError(ResponseBody(14, "Internal Server Error.").toString)
                                  case Right(v) =>
                                    dbExecutor.removeAllExpiredUserSessions(user.userId, System.currentTimeMillis())
                                    if (v == 1) Ok(jsonParser.toJSON((user, settings, chatList)))
                                    else {
                                      logger.error(s"SignIn. Cannot create user Session. userId(${loginCredentials.userId})")
                                      InternalServerError(ResponseBody(15, "Internal Server Error.").toString)
                                    }
                                }
                            }
                          })
                        }(databaseExecutionContext)
                    }
                } // no payload
              case None =>
                logger.error(s"SignIn. Creating new user Error. No payload.")
                Future.successful(BadRequest(ResponseBody(10, s"Bad Request").toString))
            } // invalid KSID header
          case None =>
            logger.error(s"SignIn. Failed KSID header parsing.")
            Future.successful(Unauthorized(ResponseBody(9, s"Unauthorized").toString))
        } // no KSID header
      case None =>
        logger.error(s"SignIn. No KSID header.")
        Future.successful(Unauthorized(ResponseBody(8, s"Unauthorized").toString))
    }
  }





  def logout: Action[AnyContent] = Action.async { implicit request =>
    request.headers.get("KSID") match {
      case Some(ksid) =>
        headersParser.parseKSID(ksid) match {
          case Some(sessionData) =>
            Future {
              db.withConnection(implicit connection => {
                dbExecutor.removeSession(sessionData.sessionId, sessionData.userId) match {
                  case Left(_) =>
                    logger.warn(s"Cannot remove current session from DB.")
                    InternalServerError("Error 018. Db Error. ")
                  case Right(v) =>
                    if (v == 1) {
                      logger.trace(s"Successfull logout. userId(${sessionData.userId.toString})")
                      Ok(ResponseBody(0, "Logout successfully.").toString)
                    }
                    else {
                      logger.warn(s"Logout accepted, but no matching session in DB. userId(${sessionData.userId.toString})")
                      Accepted(ResponseBody(0, "Not matching session.").toString)
                    }
                }
              })
            }(databaseExecutionContext)
          // invalid KSID header
          case None =>
            logger.error(s"Logout. Failed KSID header parsing.")
            Future.successful(Unauthorized(ResponseBody(17, s"Unauthorized").toString))
        } // no KSID header
      case None =>
        logger.trace(s"Logout. No KSID header.")
        Future.successful(Accepted(ResponseBody(16, s"Logout accepted without credentials.").toString))
    }
  }





  def user(userId: UUID): Action[AnyContent] =
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
          dbExecutor.getUserData( userId ) match {
            case Left(qe) =>
              logger.warn(s"Cannot get user's data. Error ${qe.description.toString()}. userId(${userId.toString})")
              InternalServerError(ResponseBody(9,s"${qe.description.toString()}").toString())
            case Right(t) =>
              logger.trace(s"Returning user's data normally. userId(${userId.toString})")
              Ok(jsonParser.toJSON( t ))
          }
        })
      }(databaseExecutionContext)
    })




  def updateJoiningOffset(userId: UserID): Action[AnyContent] =
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
      request.body.asJson.map( s => {
        parseUserOffsetUpdate(s.toString()) match {
          case Left(_) =>
            logger.error(s"Cannot parse user's offset update data. userId(${userId.toString})")
            Future.successful(InternalServerError(ResponseBody(333, s"Internal Server Error.").toString()))
          case Right(uou) =>
            if (uou.joiningOffset > 0L) {
              Future {
                db.withConnection(implicit connection => {
                  dbExecutor.updateJoiningOffset(userId, uou.joiningOffset) match {
                    case Left(qe) =>
                      logger.warn(s"Cannot update user's offset. userId(${userId.toString})")
                      InternalServerError(ResponseBody(44, s"Database Error: ${qe.description.toString()}.").toString)
                    case Right(_) =>
                      logger.trace(s"User's offset updated. userId(${userId.toString})")
                      Ok
                  }
                })
              }(databaseExecutionContext)
            }
            else {
              logger.warn(s"Incompatible user's offset value = ${uou.joiningOffset}. userId(${userId.toString})")
              Future.successful(BadRequest(ResponseBody(45, s"Offset value should be above 0.").toString))
            }
        }
      }).getOrElse({
        logger.error(s"UpdateJoiningOffset. Cannot parse payload. userId(${userId.toString})")
        Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString())) // s"Error 028. Cannot parse payload data. "
      })
    })




  // todo works
  def changeSettings(userId: UUID): Action[AnyContent] =
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
          case Left(_) =>
            logger.error(s"changeSettings. Cannot parse payload. userId(${userId.toString})")
            Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString()))
          case Right(settings) =>
            Future {
              db.withConnection( implicit connection => {
                dbExecutor.updateSettings(userId, settings) match {
                  case Left(qe) =>
                    logger.warn(s"Cannot update user's offset. userId(${userId.toString})")
                    InternalServerError(ResponseBody(44, s"Database Error: ${qe.description.toString()}.").toString)
                  case Right(v) =>
                    if (v == 1) {
                      logger.trace(s"ChangeSettings. New settings updated in DB. userId(${userId.toString})")
                      Ok(ResponseBody(0, s"New Settings Saved.").toString)
                    }
                    else {
                      logger.trace(s"ChangeSettings. Nothing updated in DB. userId(${userId.toString})")
                      Accepted(ResponseBody(0, s"Nothing to updated.").toString)
                    }
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse({
        logger.error(s"ChangeSettings. Cannot parse payload. userId(${userId.toString})")
        Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString())) // s"Error 028. Cannot parse payload data. "
      })
    })





  // todo works
  def changeLogin(userId: UUID): Action[AnyContent] =
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
                  case LoginTaken =>
                    logger.trace(s"Cannot change login, login is taken. userId(${userId.toString})")
                    BadRequest(ResponseBody(29, "Login taken. Try with another one.").toString  )
                  case _ =>
                    logger.warn(s"ChangeLogin. Database Error: ${m.toString()}. userId(${userId.toString})")
                    InternalServerError(ResponseBody(30, s"Database Error: ${m.toString()}").toString)
                }
              case Right(i) =>
                if (i == 1) {
                  logger.trace(s"New Login updated. userId(${userId.toString})")
                  Ok(ResponseBody(0, "Login successfully changed!!!").toString)
                }
                else {
                  logger.warn(s"Cannot update login. userId(${userId.toString})")
                  BadRequest(ResponseBody(31, "Oppsss, User not found???").toString)
                }
            }
          })
        }(databaseExecutionContext)
      }).getOrElse({
        logger.error(s"ChangeLogin. Cannot parse payload. userId(${userId.toString})")
        Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString())) // s"Error 028. Cannot parse payload data. "
      })
    })





  // todo works
  def changePassword(userId: UUID): Action[AnyContent] =
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
          case Left(_) =>
            logger.error(s"changePassword. Cannot parse payload. userId(${userId.toString})")
            Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString()))
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
                      case Left(qe) =>
                        logger.warn(s"changePassword. Database Error: ${qe.description.toString()}. userId(${userId.toString})")
                        InternalServerError(ResponseBody(30, s"Database Error: ${qe.description.toString()}").toString)
                      case Right(v) =>
                        if (v == 1) {
                          logger.trace(s"changePassword. New password updated. userId(${userId.toString})")
                          Ok(ResponseBody(0, "New Password saved.").toString)
                        }
                        else {
                          logger.warn(s"changePassword. Cannot update password. userId(${userId.toString})")
                          BadRequest(ResponseBody(22,  s"Old Password does not match.").toString)
                        }
                    }
                  })
                }(databaseExecutionContext)
              case _ =>
                logger.error(s"changePassword. Cannot parse one or two passwords. userId(${userId.toString})")
                Future.successful(InternalServerError(ResponseBody(28, s"Request failed.").toString()))
            }
        }
      }).getOrElse({
        logger.error(s"changePassword. Cannot parse payload. userId(${userId.toString})")
        Future.successful(BadRequest(ResponseBody(28, s"Request failed.").toString()))
      })
    })





  def searchUser(userId: UUID, u: String): Action[AnyContent] =
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
      Future {
        db.withConnection(implicit connection => {
          dbExecutor.findUser(u) match {
            case Left(qe) =>
              logger.warn(s"searchUser. Database Error: ${qe.description.toString()}. userId(${userId.toString})")
              InternalServerError(ResponseBody(10, s"Database Error: ${qe.description.toString()}").toString)
            case Right(found) =>
              if (found.isEmpty) {
                logger.trace(s"searchUser. No user found. userId(${userId.toString})")
                NoContent
              } else {
                logger.trace(s"searchUser. User '$u' found. userId(${userId.toString})")
                Ok(toJSON(found))
              }
          }
        })
      }(databaseExecutionContext)
    })





  def newChat(userId: UUID): Action[AnyContent] =
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
      request.body.asJson.map(json => {
        jsonParser.parseNewChat(json.toString()) match {
          case Left(_) =>
            logger.error(s"changePassword. Cannot parse payload. userId(${userId.toString})")
            Future.successful(BadRequest(ResponseBody(11, s"Request failed.").toString()))
          case Right((me, users, chatName)) =>
            Future {
              db.withConnection(implicit connection => {
                dbExecutor.createChat(me, users, chatName) match {
                  case Left(QueryError(_, UnsupportedOperation)) =>
                    tutuaj
                    logger.
                    BadRequest(ResponseBody(12, "Chat already exists.").toString)
                  case Left(queryError) =>
                    InternalServerError(s"Error 013. ${queryError.description.toString()}")
                  case Right( createdChat ) =>
                    //val ka = new KafkaAdmin(new KafkaProductionConfigurator)
                    kafkaAdmin.createChat(createdChat.head._1) match {
                      case Left(_) => // not rechable
                        kafkaAdmin.removeChat( createdChat.head._1 )
                        //ka.closeAdmin()
                        dbExecutor.deleteChat( createdChat.head._1.chatId ) // delete chat from db
                        InternalServerError(ResponseBody(332, "Some Undefined Kafka Error.").toString)
                      case Right( t ) =>
                        if ( t._1 && t._2  ) { // if chat created we can send invitations it to user
                          val producer = kafkaAdmin.createInvitationProducer
                          users.foreach( userId => {
                            val partitionOffsets = (0 until configurator.CHAT_TOPIC_PARTITIONS_NUMBER)
                              .map(i => PartitionOffset(i, 0L)).toList
                            val i = Invitation(me.login, userId, chatName, createdChat.head._1.chatId,
                              System.currentTimeMillis(), 0L, partitionOffsets, None)
                            val joiningTopic = Domain.generateJoinId( userId )
                            producer.send(new ProducerRecord[String, Invitation](joiningTopic, i))
                          })
                          producer.close()
                          //ka.closeAdmin()
                          Ok(jsonParser.chatsToJSON(createdChat))
                        } else {
                          kafkaAdmin.removeChat( createdChat.head._1 )
                          //ka.closeAdmin()
                          dbExecutor.deleteChat( createdChat.head._1.chatId )
                          InternalServerError(ResponseBody(333, "Cannot create new chat. Try again later.").toString)
                        }
                    }
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(BadRequest("Error 014, JSON parsing error.")))
    })





  def getChats(userId: UUID): Action[AnyContent] =
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
      Future {
        db.withConnection(implicit connection => {
          dbExecutor.findMyChats(userId) match {  // todo prewiously findMyChats()
            case Left(_) => InternalServerError("Error 009. You are logged in with SessionChecker and SessionUpdater ")
            case Right(chats) =>
              //val c = chats.filter(t => !t._2._2).map(t => (t._1, t._2._1))
              Ok( jsonParser.chatsToJSON( chats ))
          }
          })
      }(databaseExecutionContext)
    })





  def getChatData(userId: UUID, chatId: ChatId): Action[AnyContent] =
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
          dbExecutor.getChatData(userId, chatId) match {
            case Left(_) => InternalServerError("Error 021. Database Error")
            case Right(chatData) => Ok( jsonParser.chatToJSON( chatData ))
          }
        })
      }(databaseExecutionContext)
    })




  def getChatUsers(userId: UUID, chatId: ChatId): Action[AnyContent] =
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





  def leaveChat(userId: UUID, chatId: String): Action[AnyContent] =
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





  def setChatSettings(userId: UUID, chatId: String): Action[AnyContent] =
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






  def addUsersToChat(userId: UUID, chatId: ChatId): Action[AnyContent] =
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
    ).async( implicit request =>
      request.body.asJson.map(newChatUsers => {
        jsonParser.parseNewChatUsers(newChatUsers.toString()) match {
          case Left(_) => Future.successful(BadRequest("Error 018, JSON parsing error."))
          case Right((inviters, chatName, users, partitionOffsets)) =>
            Future {
              db.withConnection { implicit connection =>
                dbExecutor.addNewUsersToChat(users, chatId, chatName, partitionOffsets) match {
                  case Left(queryError) => InternalServerError(s"Error 019. ${queryError.description.toString()}")
                  case Right(value)     =>
                    // val ka = new KafkaAdmin(new KafkaProductionConfigurator)
                    val producer = kafkaAdmin.createInvitationProducer
                    users.foreach( userId2 => {
                      val i = Invitation(inviters, userId2, chatName, chatId,
                        System.currentTimeMillis(), 0L, partitionOffsets, None)
                      val joiningTopic = Domain.generateJoinId(userId)
                      producer.send(new ProducerRecord[String, Invitation](joiningTopic, i))
                    })
                    producer.close()
                    // ka.closeAdmin()
                    Ok(ResponseBody(0,s"$value users added.").toString)
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 020. Cannot parse JSON data.")))
      )





  // dokończyć definiowanie websocketa
  //  https://www.playframework.com/documentation/2.8.x/ScalaWebSockets
  def ws(userId: UUID): WebSocket =
    WebSocket.acceptOrResult[String, String] { request =>
      Future.successful(
        request.headers.get("Origin") match {
          case Some(value) =>
            println("Request has Origin header")
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
                          //val brokerExecutor = new BrokerExecutor( out, db, new KafkaProductionConfigurator, kafkaExecutionContext)
                          // WebSocketActor.props(out, new KessengerAdmin(configurator),  kafkaExecutionContext, db, databaseExecutionContext, brokerExecutor)
                          WebSocketActor.props(out, kafkaAdmin,  kafkaExecutionContext, db, databaseExecutionContext, userId)
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


