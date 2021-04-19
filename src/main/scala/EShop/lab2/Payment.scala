package EShop.lab2

import EShop.lab2.Checkout.ReceivePayment
import EShop.lab2.Payment.{DoPayment, PaymentConfirmed}
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

object Payment{

  def props(cart: ActorRef) = Props(new Payment())

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentConfirmed extends Event
}

class Payment extends Actor {
  def receive: Receive = LoggingReceive {
    case DoPayment => {
      sender() ! PaymentConfirmed
      context.parent ! ReceivePayment    // better name PaymentReceived
    }
    case _ => println("PAYMENT PROBLEM")
  }


}
