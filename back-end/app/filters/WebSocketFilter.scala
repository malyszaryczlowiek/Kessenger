package filters

import javax.inject.Inject
import akka.stream.Materializer
import play.api.mvc.{EssentialAction, Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class WebSocketFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {


  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.headers.get("Origin") match {
        case Some(origin) =>
          println()
          println(s"Rządanie ma Origin o wartość $origin")
          result.header.headers.foreach(println)
          // result.withHeaders("Access-Control-Allow-Origin" -> "http://localhost:4200")
          result
        case None =>
          println(s"Rządanie nie ma nagłówka Origin.")
          result//.withHeaders("Access-Control-Allow-Origin" -> "http://localhost:4200") // nie zmieniamy result
      }


    }
  }



}
