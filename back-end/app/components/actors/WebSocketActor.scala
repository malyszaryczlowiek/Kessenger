package components.actors

import akka.actor._
import akka.actor.PoisonPill

import scala.concurrent.Future


object WebSocketActor {
  def props(out: ActorRef) = Props(new WebSocketActor(out))
}

class WebSocketActor(out: ActorRef) extends Actor {




  override def postStop(): Unit = {
    println(s"Wyłączyłem actora.")

    // TODO tutaj zamykamy wszystkie resources, których używaliśmy w aktorze.
    // someResource.close()
  }

  def receive: Receive = {
    case msg: String =>
      println(s"Przetwarzam wiadomość. ")
      out ! ("I received your message: " + msg)
    case _ =>
      println(s"Przetwarzam wiadomość #2. ")
      out ! ("Dostałem nieczytelną wiadomość.")
      /*
      w momencie gdy chcemy zamknąć naszego aktora
      i wywołać automatycznei postStop()
      musimy wywołać

      self ! PoisonPill

      TODO wbudować mechanizm autozamykania 15 min
        jeśli nie ma żadnej aktywność wysyłąnej przez użytkownika.
       */


  }

  println(s"Włączam aktora")

}