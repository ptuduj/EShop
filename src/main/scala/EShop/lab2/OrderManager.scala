package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CheckoutStarted, CloseCheckout, RemoveItem}
import EShop.lab2.Checkout.{CancelCheckout, CheckOutClosed, PaymentServiceStarted, ReceivePayment, SelectDeliveryMethod, SelectPayment, StartCheckout}
import EShop.lab2.EShopManager.StartManaging
import EShop.lab2.Payment.{DoPayment, PaymentConfirmed}
import akka.actor.{Actor, ActorPath, ActorRef, ActorSystem, Props, UnhandledMessage}
import akka.event.Logging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object EShopManager {
  case class StartManaging()
}

class EShopManager extends Actor {

  val log       = Logging(context.system, this)
  val cartActor = context.actorOf(CartActor.props(self))
  //val cartActor = context.system.actorOf(Props[CartFSM], "cartActor")
  val consoleActor             = context.system.actorOf(Props[ConsoleActor], "console")
  var checkoutActor: ActorRef = _
  var paymentActor: ActorRef = _

  def receive = {
    case StartManaging => {
      println(self.path)
      consoleActor ! EnableConsole
    }

    case CheckoutStarted(checkoutRef) => checkoutActor = checkoutRef
    case PaymentServiceStarted(paymentRef) => paymentActor = paymentRef
    case PaymentConfirmed => println("Payment confirmed")
    case CheckOutClosed => println("Checkout closed")

    case s: String => {
      s match {
        case s if s.matches("1 .*") =>
          cartActor ! AddItem(s.split(" ", 2)(1))
        case s if s.matches("2 .*") =>
          cartActor ! RemoveItem(s.split(" ", 2)(1))
        case s if s.matches("3") =>
          cartActor ! StartCheckout
        case s if s.matches("4 .*") =>
          checkoutActor ! SelectDeliveryMethod(s.split(" ", 2)(1))
        case s if s.matches("5 .*") =>
          checkoutActor ! SelectPayment(s.split(" ", 2)(1))
        case s if s.matches("6") =>
          paymentActor ! DoPayment
        case s if s.matches("7") =>
          checkoutActor ! CancelCheckout
        case s if s.matches("8") =>
          checkoutActor ! CloseCheckout
        case _ => print("invalid instruction")
      }
    }
  }
}

class DeadActorListener extends Actor {
  def receive = {
    case u: UnhandledMessage => println("Unhandled message " + u.message)
  }
}

object MyApp extends App {
  val system      = ActorSystem()
  val orderManager = system.actorOf(Props[EShopManager], "orderManager")
  println(orderManager.path)
  orderManager ! StartManaging

  val listener = system.actorOf(Props[DeadActorListener], "deadActor")
  system.eventStream.subscribe(listener, classOf[UnhandledMessage])

  Await.result(system.whenTerminated, Duration.Inf)

}
