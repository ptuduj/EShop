package EShop.lab2

import akka.actor.{Actor, ActorRef}
import akka.event.Logging

import scala.io.Source

case class EnableConsole()

class ConsoleActor extends Actor {
  val log                   = Logging(context.system, this)
  var shopManager: ActorRef = _

  def receive = {
    case EnableConsole => {
      log.debug("EnableConsole received")
      shopManager = sender()
      acceptUserInput
    }
  }

  def acceptUserInput = {
    println(
      """Instruction
        1 x -> add item x
        2 x -> remove item x
        3 -> start checkout
        4 x -> select delivery method x
        5 x -> select payment x
        6 -> do payment
        7 -> cancel checkout
        8 -> close checkout
      """
    )

    for (ln: String <- (Source.stdin.getLines.takeWhile(!_.equals("end")))) {
      shopManager ! ln
    }
    context.system.terminate()
  }
}
