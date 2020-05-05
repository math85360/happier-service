package happier.fr.vmmateriaux
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.api._
import happier.actor._
import akka.Done

final case class NewOrder()
case object VmMateriauxMakeOrder extends NormalizedSink {
  type ParentCommand = VmMateriauxAccountSession.Command
  type Params = Nothing
  override type In = NewOrder

  override def apply(parent: ActorRef[ParentCommand], params: Params): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case _ => Behaviors.unhandled
    }
  }
}