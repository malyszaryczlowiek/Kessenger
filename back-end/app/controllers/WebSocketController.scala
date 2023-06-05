package controllers

import akka.actor.ActorSystem
import ch.qos.logback.classic.Logger
import components.actors.WebSocketActor
import components.db.MyDbExecutor
import components.executioncontexts.{DatabaseExecutionContext, KafkaExecutionContext}
import conf.KafkaConf
import io.github.malyszaryczlowiek.kessengerlibrary.model.ResponseBody
import kafka.KafkaAdmin

import play.api.{ConfigLoader, Configuration}
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import java.util.UUID
import javax.inject._
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future}
import org.slf4j.LoggerFactory




@Singleton
class WebSocketController @Inject()
(
  val controllerComponents: ControllerComponents,
  val db: Database,
  val dbExecutor: MyDbExecutor,
  val databaseExecutionContext: DatabaseExecutionContext,
  val kafkaExecutionContext: KafkaExecutionContext,
  val kafkaAdmin: KafkaAdmin,
  @Named("KafkaConfiguration") implicit val configurator: KafkaConf,
  val lifecycle: ApplicationLifecycle,
  val conf: Configuration,
  implicit val system: ActorSystem
) extends BaseController {



  lifecycle.addStopHook { () =>
    Future.successful(kafkaAdmin.closeAdmin())
  }


  private val logger: Logger = LoggerFactory.getLogger(classOf[WebSocketController]).asInstanceOf[Logger]

  // dokończyć definiowanie websocketa
  //  https://www.playframework.com/documentation/2.8.x/ScalaWebSockets
  def ws(userId: UUID): WebSocket =
    WebSocket.acceptOrResult[String, String] { request =>
      Future.successful(
        request.headers.get("Origin") match {
          case Some(value) =>
            val webapp = conf.get("kessenger.webapp.server")(ConfigLoader.stringLoader)
            if (value == webapp) {
              val f = Future {
                db.withConnection(implicit connection => {
                  dbExecutor.getNumOfValidUserSessions(userId)
                })
              }(databaseExecutionContext)
              try {
                val result = Await.result(f, Duration.create(2L, SECONDS))
                result match {
                  case Left(_) =>
                    logger.error(s"ws. Error with waiting of DB response. userId(${userId.toString})")
                    Left(InternalServerError(ResponseBody(13, s"Internal server Error").toString()))
                  case Right(value) =>
                    // this means we have one valid session at leased
                    if (value > 0) {
                      logger.trace(s"ws. Starting Actor system. nr of active sessions: ${value} userId(${userId.toString})")
                      Right(
                        ActorFlow.actorRef { out =>
                          WebSocketActor.props(out, kafkaAdmin, kafkaExecutionContext, db, databaseExecutionContext, userId)
                        }
                      )
                    } else {
                      logger.error(s"ws. Error with waiting of DB response. userId(${userId.toString})")
                      Left(Unauthorized(ResponseBody(12, "No valid session. Login again.").toString))
                    }
                }
              } catch {
                case e: Throwable =>
                  logger.error(s"ws. Exception with calling number of DB sessions:\n${e.getMessage}\nuserId(${userId.toString})")
                  Left(InternalServerError(ResponseBody(13, s"Internal server error.").toString()))
              }
            } else {
              logger.error(s"ws. Request has not KSID header. userId(${userId.toString})")
              Left(Unauthorized(ResponseBody(11, s"Request from forbidden webapp.").toString))
            }
          case None =>
            logger.error(s"ws. Request has not KSID header. userId(${userId.toString})")
            Left(BadRequest(ResponseBody(11, s"Bad Request. Try to logout and login again.").toString))
        }
      )
    }


}
