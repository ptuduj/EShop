package EShop.lab4

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.Checkout.{CancelCheckout, ExpireCheckout, ReceivePayment, SelectDeliveryMethod, SelectPayment, StartCheckout}
import EShop.lab4.PersistentCheckout.{CheckOutClosed, CheckoutCancelled, CheckoutStarted, DeliveryMethodSelected, Event, PaymentStarted}
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object PersistentCheckout {

  sealed trait Event
  case object CheckoutStarted extends Event
  case class DeliveryMethodSelected(method: String) extends Event
  case object PaymentStarted extends Event
  case object CheckoutCancelled extends Event
  case object CheckOutClosed extends Event

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
                          cartActor: ActorRef,
                          val persistenceId: String
                        ) extends PersistentActor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds
  implicit val ec: ExecutionContext = context.system.dispatcher
  private def checkoutTimer: Cancellable =
    context.system.scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable =
    context.system.scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    event match {
      case CheckoutStarted                => context become selectingDelivery(checkoutTimer)
      case DeliveryMethodSelected(_)      => context become selectingPaymentMethod(checkoutTimer)
      case PaymentStarted             => context become processingPayment(paymentTimer)
      case CheckOutClosed                 => context become closed
      case CheckoutCancelled              => context become cancelled
    }
  }

  def receiveCommand: Receive = LoggingReceive.withLabel("inReceive") {
    case StartCheckout => {
      persist(CheckoutStarted) {
        event => updateState(event)
      }
    }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) => {
      persist((DeliveryMethodSelected(method))){
        timer.cancel()
        event => updateState(event)
      }
    }
    case CancelCheckout | ExpireCheckout => {
      persist(CheckoutCancelled) {
        timer.cancel()
        event => updateState(event)
      }
    }
    case CloseCheckout => {
      persist(CheckOutClosed) {
        timer.cancel()
        event => updateState(event)
      }
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive{
    case SelectPayment(method) => {
      persist(PaymentStarted){
        timer.cancel()
        event => updateState(event)
      }
    }
    case CancelCheckout | ExpireCheckout => {
      persist(CheckoutCancelled) {
        timer.cancel()
        event => updateState(event)
      }
    }
    case CloseCheckout => {
      persist(CheckOutClosed) {
        timer.cancel()
        event => updateState(event)
      }
    }
  }

  def processingPayment(timer: Cancellable): Receive = {
    case ReceivePayment | CloseCheckout => {
      persist(CheckOutClosed){
        timer.cancel()
        event => updateState(event)
      }
    }
    case CancelCheckout | ExpireCheckout => {
      persist(CheckoutCancelled) {
        timer.cancel()
        event => updateState(event)
      }
    }
  }

  def cancelled: Receive = LoggingReceive.withLabel("cancelled") {
    case _ => log.info("checkout cancelled")
  }

  def closed: Receive = LoggingReceive.withLabel("closed") {
    case _ => log.info("checkout closed")
  }

  override def receiveRecover: Receive = LoggingReceive{
    case event: Event => updateState(event)
  }
}
