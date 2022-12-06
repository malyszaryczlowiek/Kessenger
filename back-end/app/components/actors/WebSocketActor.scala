package components.actors

import akka.actor._
import akka.actor.PoisonPill

import scala.concurrent.Future


object WebSocketActor {
  def props(out: ActorRef) = Props(new WebSocketActor(out))
}

class WebSocketActor(out: ActorRef) extends Actor {




  override def postStop(): Unit = {
    println(s"5. Wyłączyłem actora.")

    // TODO tutaj zamykamy wszystkie resources, których używaliśmy w aktorze.
    // someResource.close()
  }

  def receive: Receive = {
    case msg: String =>
      println(s"1. Przetwarzam wiadomość. ")
      if (msg == "PoisonPill") {
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