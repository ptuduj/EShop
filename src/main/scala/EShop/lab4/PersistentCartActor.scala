package EShop.lab4

import EShop.lab2.Cart
import EShop.lab2.CartActor.{AddItem, CloseCheckout, ExpireCart, RemoveItem, StartCheckout, CancelCheckout}
import EShop.lab2.Checkout.{CheckOutClosed}
import EShop.lab4.PersistentCartActor.{CartEmptied, CartExpired, CheckoutCancelled, CheckoutClosed, CheckoutStarted, Event, ItemAdded, ItemRemoved}
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object PersistentCartActor {

  sealed trait Event
  case class ItemAdded(cart: Cart) extends Event
  case class ItemRemoved(cart: Cart) extends Event
  case object CartEmptied extends Event
  case object CartExpired extends Event
  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event
  case object CheckoutClosed extends Event
  case class CheckoutCancelled(cart: Cart) extends Event


  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor (val persistenceId: String) extends PersistentActor {

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  implicit val ec: ExecutionContext = context.system.dispatcher
  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    event match {
      case ItemAdded(cart)                    => context become nonEmpty(cart, scheduleTimer)
      case ItemRemoved(cart)                  => context become nonEmpty(cart, scheduleTimer)
      case CartEmptied                        => context become empty
      case CheckoutStarted(checkoutRef, cart) => {
        checkoutRef ! StartCheckout
        //sender() ! CheckoutStarted
        context become inCheckout(cart)
      }
      case CartExpired | CheckoutClosed       => context become empty
      case CheckoutCancelled(cart)            => context become nonEmpty(cart, scheduleTimer)
    }
  }

  def empty: Receive = LoggingReceive.withLabel("empty") {
    case AddItem(item) => {
      persist(ItemAdded(Cart.empty.addItem(item))) {
        event => updateState(event)
      }
    }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive.withLabel("nonEmpty"){
    case AddItem(item) => {
      persist(ItemAdded(cart.addItem(item))) {
        timer.cancel()
        event => updateState(event)
      }
    }
    case RemoveItem(item) if cart.size == 1 && cart.contains(item) => {
      persist(CartEmptied) {
        timer.cancel()
        event => updateState(event)
      }
    }
    case RemoveItem(item) if cart.contains(item) => {
      persist(ItemRemoved(cart.removeItem(item))) {
        timer.cancel()
        event => updateState(event)
      }
    }
    case StartCheckout => {
      persist(CheckoutStarted(context.actorOf(PersistentCheckout.props(self, "Persistence-checkout-ID")), cart)){
        timer.cancel()
        event => updateState(event)
      }
    }
    case ExpireCart => {
      persist(CartExpired){
        timer.cancel()
        event => updateState(event)
      }
    }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive.withLabel("inCheckout") {
    case CancelCheckout => {
      persist(CheckoutCancelled(cart)) {
        event => updateState(event)
      }
    }
    case CloseCheckout  =>  {
      persist(CheckoutClosed) {
        event => updateState(event)
      }
    }
  }

  override def receiveRecover: Receive = LoggingReceive{
    case event: Event => updateState(event)
  }
}