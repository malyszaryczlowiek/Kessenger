package controllers

import ch.qos.logback.classic.Logger
import components.actions.{SessionChecker, SessionUpdater}
import components.db.MyDbExecutor
import components.executioncontexts.{DatabaseExecutionContext, KafkaExecutionContext}
import conf.KafkaConf
import kafka.KafkaAdmin
import util.{HeadersParser, JsonParsers}

import io.github.malyszaryczlowiek.kessengerlibrary.db.queries.{DataProcessingError, QueryError, UnsupportedOperation}
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model.{Chat, Invitation, PartitionOffset, ResponseBody}
import io.github.malyszaryczlowiek.kessengerlibrary.model.Chat.parseJSONtoChat
import io.github.malyszaryczlowiek.kessengerlibrary.model.User.toJSON

import org.apache.kafka.clients.producer.ProducerRecord
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.mvc._

import java.util.UUID
import javax.inject._

import scala.concurrent.Future
import org.slf4j.LoggerFactory





@Singleton
class ChatsController @Inject()
  (
    val controllerComponents: ControllerComponents,
    val db: Database,
    val dbExecutor: MyDbExecutor,
    val jsonParser: JsonParsers,
    val headersParser: HeadersParser,
    val databaseExecutionContext: DatabaseExecutionContext,
    val kafkaExecutionContext: KafkaExecutionContext,
    val kafkaAdmin: KafkaAdmin,
    @Named("KafkaConfiguration") implicit val configurator: KafkaConf,
    val lifecycle: ApplicationLifecycle,
  ) extends BaseController {

//  val config = Configuration.load(Environment.simple(new File("./conf/application.conf"), Mode.Dev))
//  val foo = config.  getString("kessenger.kafka.broker.hosts").getOrElse("NIE MA")



  private val logger: Logger = LoggerFactory.getLogger(classOf[ChatsController]).asInstanceOf[Logger]



  lifecycle.addStopHook { () =>
    Future.successful(kafkaAdmin.closeAdmin())
  }





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
            logger.error(s"newChat. Cannot parse payload. userId(${userId.toString})")
            Future.successful(BadRequest(ResponseBody(11, s"Request failed.").toString()))
          case Right((me, users, chatName)) =>
            Future {
              db.withConnection(implicit connection => {
                dbExecutor.createChat(me, users, chatName) match {
                  case Left(QueryError(_, UnsupportedOperation)) =>
                    logger.warn(s"newChat. ChatId already exists. userId(${userId.toString})")
                    BadRequest(ResponseBody(12, "Database Error, Try again.").toString)
                  case Left(queryError) =>
                    logger.warn(s"newChat. Database error: ${queryError.description.toString()}. userId(${userId.toString})")
                    InternalServerError(ResponseBody(13, s"Database Error: ${queryError.description.toString()}").toString )
                  case Right( createdChat ) =>
                    kafkaAdmin.createChat(createdChat.head._1) match {
                      case Left(_) => // not reachable
                        kafkaAdmin.removeChat( createdChat.head._1 )
                        dbExecutor.deleteChat( createdChat.head._1.chatId ) // delete chat from db
                        logger.error(s"newChat. Cannot create new Kafka chat topic. userId(${userId.toString})")
                        InternalServerError(ResponseBody(332, "Kafka Broker Error.").toString)
                      case Right( t ) =>
                        if ( t._1 && t._2  ) { // if chat created we can send invitations to user
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
                          logger.trace(s"newChat. Chat ${createdChat.head._1.chatId} created. userId(${userId.toString})")
                          Ok(jsonParser.chatsToJSON(createdChat))
                        } else {
                          kafkaAdmin.removeChat( createdChat.head._1 )
                          dbExecutor.deleteChat( createdChat.head._1.chatId )
                          logger.error(s"newChat. Cannot create new chat. userId(${userId.toString})")
                          InternalServerError(ResponseBody(333, "Cannot create new chat. Try again later.").toString)
                        }
                    }
                }
              })
            }(databaseExecutionContext)
        }
      }).getOrElse(
        {
          logger.error(s"newChat. JSON parsing error. userId(${userId.toString})")
          Future.successful(BadRequest(ResponseBody(14, s"JSON parsing error.").toString))
        }
      )
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
            case Left(err) =>
              logger.error(s"getChats. Database Error: ${err.description.toString}. userId(${userId.toString})")
              InternalServerError(ResponseBody(9, s"Database Error: ${err.description.toString}").toString())
            case Right(chats) =>
              logger.trace(s"getChats. Replaying with chats. userId(${userId.toString})")
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
            case Left(err) =>
              logger.error(s"getChatData. Database Error: ${err.description.toString}. userId(${userId.toString})")
              InternalServerError(ResponseBody(21, s"Database Error: ${err.description.toString}").toString())
            case Right(chatData) =>
              logger.trace(s"getChatData. Replaying with chat's data. userId(${userId.toString})")
              Ok( jsonParser.chatToJSON( chatData ))
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
            case Left(err) =>
              logger.error(s"getChatUsers. Database Error: ${err.description.toString}. userId(${userId.toString})")
              InternalServerError(ResponseBody(21, s"Database Error: ${err.description.toString}").toString())
            case Right(listOfUser) =>
              logger.trace(s"getChatUsers. Replaying with chat's users. userId(${userId.toString})")
              Ok(toJSON(listOfUser))
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
              logger.error(s"leaveChat. Cannot leave this type of chat. userId(${userId.toString})")
              BadRequest(ResponseBody(22, s"Unsupported operation").toString())
            case Left(QueryError(_, DataProcessingError)) =>
              logger.error(s"leaveChat. DataProcessing Database Error. userId(${userId.toString})")
              InternalServerError(ResponseBody(23, s"Data Processing Error.").toString())
            case Left(err) =>
              logger.error(s"leaveChat. Database Error: ${err.description.toString}. userId(${userId.toString})")
              InternalServerError(ResponseBody(24, s"Error: ${err.description.toString()}").toString())
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
            logger.error(s"setChatSettings. Parsing Payload Error. userId(${userId.toString})")
            Future.successful(InternalServerError(ResponseBody(28, s"Parsing Payload Error").toString()))
          case Right(chat: Chat) =>
            Future {
              db.withConnection { implicit connection =>
                dbExecutor.updateChat(userId, chat) match {
                  case Left(queryError) =>
                    logger.error(s"setChatSettings. Error: ${queryError.description.toString()}. userId(${userId.toString})")
                    InternalServerError(ResponseBody(26, s"Error: ${queryError.description.toString()}").toString)
                  case Right(value) =>
                    if (value == 1) {
                      logger.trace(s"setChatSettings. New chat settings saved. userId(${userId.toString})")
                      Ok(ResponseBody(0, "Settings saved").toString)
                    }
                    else {
                      logger.error(s"setChatSettings. Cannot save new chat settings. userId(${userId.toString})")
                      BadRequest(ResponseBody(27, "Cannot change chat settings.").toString)
                      // User is not participant of chat or chat does not exist.
                    }
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse({
        logger.error(s"setChatSettings. Cannot parse payload data. userId(${userId.toString})")
        Future.successful(BadRequest(ResponseBody(28, s"Cannot parse payload data.").toString))
      })
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
          case Left(_) =>
            logger.error(s"addUsersToChat. Cannot parse payload. userId(${userId.toString})")
            Future.successful(BadRequest(ResponseBody(18, s"Bad Request.").toString))
          case Right((inviters, chatName, users, partitionOffsets)) =>
            Future {
              db.withConnection { implicit connection =>
                dbExecutor.addNewUsersToChat(users, chatId, chatName, partitionOffsets) match {
                  case Left(queryError) =>
                    logger.error(s"addUsersToChat. Database Error: ${queryError.description.toString()}. userId(${userId.toString})")
                    InternalServerError(ResponseBody(19, s"Database Error: ${queryError.description.toString()}").toString())
                  case Right(value)     =>
                    val producer = kafkaAdmin.createInvitationProducer
                    users.foreach( userId2 => {
                      val i = Invitation(inviters, userId2, chatName, chatId,
                        System.currentTimeMillis(), 0L, partitionOffsets, None)
                      val joiningTopic = Domain.generateJoinId(userId)
                      producer.send(new ProducerRecord[String, Invitation](joiningTopic, i))
                    })
                    producer.close()
                    logger.trace(s"addUsersToChat. $value users added. userId(${userId.toString})")
                    Ok(ResponseBody(0,s"$value users added.").toString)
                }
              }
            }(databaseExecutionContext)
        }
      }).getOrElse(Future.successful(InternalServerError(s"Error 020. Cannot parse JSON data.")))
    )









}


