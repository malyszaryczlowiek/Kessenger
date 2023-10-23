package filters

import javax.inject.Inject
import akka.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger

class WebSocketFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  private val logger: Logger = LoggerFactory.getLogger(classOf[WebSocketFilter]).asInstanceOf[Logger]


  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.headers.get("Origin") match {
        case Some(origin) =>
          logger.trace(s"WebSocketFilter. Starting KafkaAdmin.")

          println(s"Rządanie ma Origin o wartość $origin")

          result.header.headers.foreach(println)
          // result.withHeaders("Access-Control-Allow-Origin" -> "http://localhost:4200")
          result
        case None =>
          println(s"Rządanie nie ma nagłówka Origin.")

          logger.trace(s"WebSocketFilter. Starting KafkaAdmin.")
          result//.withHeaders("Access-Control-Allow-Origin" -> "http://localhost:4200") // nie zmieniamy result
      }


    }
  }



}
