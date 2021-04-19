package EShop.lab5

import EShop.lab5.ProductCatalog.{GetItems, Items}
import akka.actor.{Actor, ActorSystem, Props, Status}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.{PipeToSupport, ask}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class ProductCatalogAPIServer(ac: ActorSystem) extends HttpApp {
  private implicit val timeout: Timeout     = Timeout(10 seconds)
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val router = ac.actorOf(RoundRobinPool(3).props(Props[RequestsHandler]))


  import EShop.lab5.ProductCatalog._
  override protected def routes: Route = {
    path("find") {
      post {
        entity(as[GetItems]) { query =>
          complete {
            val f = (router ? query)
              f.mapTo[Items]
          }
        }
      }
    }
  }
}

class RequestsHandler() extends Actor with PipeToSupport {
  private implicit val timeout: Timeout     = Timeout(10 seconds)
  private implicit val ec: ExecutionContext = ExecutionContext.global

  override def receive: Receive = {
    case getItems: GetItems => {
     val result =
       for {
         actor <- context.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productCatalog-*").resolveOne()
         response <- actor ? getItems
       } yield response
      result.pipeTo(sender())

//      val f  = result.andThen {
//        case Success(r : Items) => {
//         println("SUCCES ", r)
//         sender() ! r
//      }
//        case Failure(f) ⇒ {
//          println("Failure")
//          sender() ! Status.Failure(f)
//        }
//      }

    }
  }
}

object ProductCatalogAPIServer extends App {
  private val config  = ConfigFactory.load()
  // product catalog actor system
  private val productCatalogSystem = ActorSystem("ProductCatalog", config.getConfig("productcatalog").
    withFallback(config))
//
//  // product catalog actors
  val productCatalogWorkers = (0 until 4).map { i =>
   productCatalogSystem.actorOf(ProductCatalog.props(new SearchService()), "productCatalog-" + i)
  }

  // api product catalog actor system
  val apiActorSystem = ActorSystem("api", config.getConfig("api").withFallback(config))

  val server = new ProductCatalogAPIServer(apiActorSystem)
  server.startServer("localhost", 9001, apiActorSystem)

}