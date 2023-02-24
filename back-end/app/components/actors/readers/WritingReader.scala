package components.actors.readers

import akka.actor._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.ChatId
import io.github.malyszaryczlowiek.kessengerlibrary.model._
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import util.KessengerAdmin

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success, Using}


class WritingReader(out: ActorRef, parentActor: ActorRef, conf: Configuration, ka: KessengerAdmin, ec: ExecutionContext) extends Reader {


  private val chats: TrieMap[ChatId, Unit] = TrieMap.empty
  private val newChats: TrieMap[ChatId, Unit] = TrieMap.empty
  private val continueReading: AtomicBoolean = new AtomicBoolean(true)
  private var fut: Option[Future[Unit]] = None

  initializeChats()
  startReading()


  private def initializeChats(): Unit = {
    this.chats.addAll(this.conf.chats.map(c => (c.chatId, {})))
  }


  override def startReading(): Unit = {
    this.fut = Option(futureBody())
  }


  private def futureBody(): Future[Unit] = {
    Future {
      Using(this.ka.createWritingConsumer(conf.me.userId.toString)) {
        consumer => {
          initializeConsumer(consumer)
          readingLoop(consumer)
        }
      } match {
        case Failure(exception) =>
          println(s"WritingReader --> Future EXCEPTION ${exception.getMessage}")
          // if reading ended with error we need to close all actor system
          // and give a chance for web app to restart.
          out ! ResponseBody(44, "Kafka connection lost. Try refresh page in a few minutes.").toString
          Thread.sleep(250)
          parentActor ! PoisonPill
        case Success(_) => println(s"WritingReader --> Future closed correctly.")
      }
    }(ec)
  }


  override protected def initializeConsumer[Writing](consumer: KafkaConsumer[String, Writing]): Unit = {
    val writingTopics = this.chats.map(t => Domain.generateWritingId(t._1))
    consumer.subscribe(CollectionConverters.asJavaCollection(writingTopics))
  }


  @tailrec
  private def readingLoop(consumer: KafkaConsumer[String, Writing]): Unit = {
    if (this.newChats.nonEmpty) reassignConsumer(consumer)
    if (this.continueReading.get()) {
      read(consumer)
      readingLoop(consumer)
    }
  }


  private def reassignConsumer(consumer: KafkaConsumer[String, Writing]): Unit = {
    consumer.unsubscribe()
    newChats.foreach(kv => this.chats.put(kv._1, kv._2))
    initializeConsumer(consumer)
    newChats.clear()
  }


  private def read(consumer: KafkaConsumer[String, Writing]): Unit = {
    val writings: ConsumerRecords[String, Writing] = consumer.poll(java.time.Duration.ofMillis(100))
    writings.forEach(
      (r: ConsumerRecord[String, Writing]) => out ! Writing.toWebsocketJSON(r.value())
    )
  }


  override def stopReading(): Unit = {
    this.continueReading.set(false)
  }


  override def addNewChat(newChat: ChatPartitionsOffsets): Unit = {
    this.newChats.addOne(newChat.chatId -> {})
  }


  override def fetchOlderMessages(chatId: ChatId): Unit = {}



  //  def isReading: Boolean = {
  //    this.fut.isDefined && ! this.fut.get.isCompleted && this.continueReading.get()
  //  }


  // we start reading strait away

}
