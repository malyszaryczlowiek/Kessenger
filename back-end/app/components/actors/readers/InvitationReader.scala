package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model._
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import util.KessengerAdmin

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Using}




class InvitationReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KessengerAdmin, ec: ExecutionContext) extends Reader {

  private val continueReading: AtomicBoolean = new AtomicBoolean(true)
  private var fut: Option[Future[Unit]] = None
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
          println(s"InvitationReader --> Future EXCEPTION ${exception.getMessage}")
          // if reading ended with error we need to close all actor system
          // and give a chance for web app to restart.
          out ! ResponseBody(44, "Kafka connection lost. Try refresh page in a few minutes.").toString
          Thread.sleep(250)
          parentActor ! PoisonPill
        case Success(_) => println(s"InvitationReader --> Future closed correctly.")
      }
    }(ec)
  }



  override protected def initializeConsumer[Invitation](consumer: KafkaConsumer[String, Invitation]): Unit = {
    val myJoinTopic = new TopicPartition(Domain.generateJoinId(conf.me.userId), 0)
    // assign this toopic to kafka consumer
    consumer.assign(java.util.List.of(myJoinTopic))
    // and assign offset for that topic partition
    consumer.seek(myJoinTopic, conf.joiningOffset)
    println(s"InvitationReader --> Invitation consumer initialized. ")
  }



  @tailrec
  private def poolInvitations(invitationConsumer: KafkaConsumer[String, Invitation]): Unit = {
    if (this.continueReading.get()) {
      val invitations: ConsumerRecords[String, Invitation] = invitationConsumer.poll(java.time.Duration.ofMillis(250))
      invitations.forEach(
        (r: ConsumerRecord[String, Invitation]) => {
          val i: Invitation = r.value().copy(myJoiningOffset = Option(r.offset() + 1L))
          println(s"InvitationReader --> sending Invitation to web app.")
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
