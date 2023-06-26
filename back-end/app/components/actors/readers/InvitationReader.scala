package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model._
import kafka.KafkaAdmin
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Using}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}




class InvitationReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KafkaAdmin,
                       ec: ExecutionContext, actorGroupID: UUID) extends Reader {

  private val continueReading: AtomicBoolean = new AtomicBoolean(true)
  private var fut: Option[Future[Unit]] = None
  private val logger: Logger = LoggerFactory.getLogger(classOf[InvitationReader]).asInstanceOf[Logger]
  logger.trace(s"InvitationReader. Starting reader. actorGroupID(${actorGroupID.toString})")

  startReading()



  override def startReading(): Unit = {
    this.fut = Option(futureBody())
  }


  private def futureBody(): Future[Unit] = {
    Future {
      Using(this.ka.createInvitationConsumer(conf.me.userId.toString)) {
        consumer => {
          initializeConsumer(consumer)
          poolInvitations(consumer)
        }
      } match {
        case Failure(exception) =>
          logger.error(s"InvitationReader. Exception thrown: ${exception.getMessage}. actorGroupID(${actorGroupID.toString})")
          // if reading ended with error we need to close all actor system
          // and give a chance for web app to restart.
          out ! ResponseBody(44, "Kafka connection lost. Try refresh page in a few minutes.").toString
          Thread.sleep(250)
          parentActor ! PoisonPill
        case Success(_) =>
          logger.trace(s"InvitationReader. Consumer closed normally. actorGroupID(${actorGroupID.toString})")
      }
    }(ec)
  }



  override protected def initializeConsumer[String, Invitation](consumer: KafkaConsumer[String, Invitation]): Unit = {
    val myJoinTopic = new TopicPartition(Domain.generateJoinId(conf.me.userId), 0)
    // assign this toopic to kafka consumer
    consumer.assign(java.util.List.of(myJoinTopic))
    // and assign offset for that topic partition
    consumer.seek(myJoinTopic, conf.joiningOffset)
    logger.trace(s"InvitationReader. Consumer initialized normally. actorGroupID(${actorGroupID.toString})")
  }



  @tailrec
  private def poolInvitations(invitationConsumer: KafkaConsumer[String, Invitation]): Unit = {
    if (this.continueReading.get()) {
      val invitations: ConsumerRecords[String, Invitation] = invitationConsumer.poll(java.time.Duration.ofMillis(250))
      invitations.forEach(
        (r: ConsumerRecord[String, Invitation]) => {
          val i: Invitation = r.value().copy(myJoiningOffset = Option(r.offset() + 1L))
          logger.trace(s"InvitationReader. Got Invitation. actorGroupID(${actorGroupID.toString})")
          out ! Invitation.toWebsocketJSON(i)
        }
      )
      poolInvitations(invitationConsumer)
    }

  }



  override def stopReading(): Unit = {
    this.continueReading.set(false)
  }


  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {}


  override def fetchOlderMessages(chatId: ChatId): Unit = {}

}
