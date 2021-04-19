package EShop.lab2

import EShop.lab2.CartActor.{AddItem, RemoveItem}
import akka.actor.{ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.language.postfixOps

class CartActorTestRefTest extends TestKit(ActorSystem("CartActorSystem"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate
  }

    "Items should be added to cart and removed" in {
      val actorRef = TestActorRef[CartActor]
      val actor = actorRef.underlyingActor
      actorRef ! AddItem(77)
      actorRef ! AddItem(22)
      assert (actor.m_cart.size == 2)
      actorRef ! RemoveItem(22)
      assert (actor.m_cart.size == 1)
      assert (actor.m_cart.contains(77))
    }
  }
