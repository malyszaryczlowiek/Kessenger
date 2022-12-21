package components.actors

import akka.actor._
import akka.actor.PoisonPill
import components.util.converters.JsonParsers

import scala.concurrent.Future


object WebSocketActor {
  def props(out: ActorRef, jsonParser: JsonParsers) = Props(new WebSocketActor(out, jsonParser))
}

class WebSocketActor(out: ActorRef, jsonParser: JsonParsers) extends Actor {




  override def postStop(): Unit = {
    println(s"5. Wyłączyłem actora.")

    // TODO here we cloase all resources used in actor
    // tutaj będzie trzeba zpisywać wszystkie dane o tym która
    // wiadomość (która partycja którego czatu jaki ma offset) została przeczytana
    // i to będę zpisywał w db

    // someResource.close()
  }

  def receive: Receive = {
    case msg: String =>
      println(s"1. Przetwarzam wiadomość. $msg")

      jsonParser.parseMessage(msg) match {
        case Left(_) =>
          println(s"CANNOT PARSE MESSAGE")
        case Right(message) =>
          println(s"Message processed normally")
          out ! jsonParser.toJSON( message )
      }



      if (msg.equals("PoisonPill")) {
        println(s"4. Otrzymałem PoisonPill $msg")
        out ! ("4. Wyłączam czat.")
        self ! PoisonPill
      }
      else {

        // wysyłanie wiadomości z powrotem
        out ! (msg)
      }
    case _ =>
      println(s"3a. Nieczytelna wiadomość. ")
      out ! ("3b. Dostałem nieczytelną wiadomość.")
      /*
      w momencie gdy chcemy zamknąć naszego aktora
      i wywołać automatycznei postStop()
      musimy wywołać

      self ! PoisonPill

      TODO wbudować mechanizm autozamykania 15 min
        jeśli nie ma żadnej aktywność wysyłąnej przez użytkownika.
       */


  }

  println(s"0. Włączam aktora")

}