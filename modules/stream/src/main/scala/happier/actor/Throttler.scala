package happier.actor

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.annotation.DoNotInherit
import akka.stream.CompletionStrategy
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.util.ByteString
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.protobufv3.internal.Service
import scala.concurrent.Future
import akka.NotUsed
import scala.collection.immutable.Queue
import scala.reflect.ClassTag
import akka.actor.Cancellable

object Throttler {
  sealed trait InternalCommand
  sealed trait Command extends InternalCommand
  case class WantToPass(replyTo: ActorRef[Message]) extends Command
  case object RequestLimitExceeded extends Command
  case object Open extends InternalCommand

  sealed trait Message
  case object MayPass extends Message

  final type Ref = ActorRef[Command]

  def apply(strategy: ThrottlerStrategy): Behavior[Command] = strategy()
}

/**
 * Allow to customize throttler :
 * - allow only one request by time unit
 * - allow any request until RequestLimitExceeded received
 */
trait ThrottlerStrategy {
  def apply(): Behavior[Throttler.Command]
}
object ThrottlerStrategy {
  final case class AllowOnlyOneRequestByTimeUnit(
    requestTimeout: FiniteDuration,
    requestLimitExceededTimeout: FiniteDuration) extends AllowOnlyOneRequestByTimeUnitThrottlerStrategy
}
sealed abstract class AllowOnlyOneRequestByTimeUnitThrottlerStrategy extends ThrottlerStrategy {
  import Throttler._

  def requestTimeout: FiniteDuration
  def requestLimitExceededTimeout: FiniteDuration

  override def apply(): Behavior[Throttler.Command] = open().narrow

  private def open(): Behavior[InternalCommand] = Behaviors.receive { (context, message) =>
    message match {
      case WantToPass(replyTo) =>
        replyTo ! MayPass
        close(Queue.empty, scheduleNextRequest(context))
      case RequestLimitExceeded =>
        close(Queue.empty, scheduleLimitExceeded(context))
      case Open =>
        throw new Exception("Open received while already in open state")
    }
  }

  private def close(waitQueue: Queue[ActorRef[Message]], cancellable: Cancellable): Behavior[InternalCommand] = Behaviors.receive { (context, message) =>
    message match {
      case WantToPass(replyTo) =>
        close(waitQueue.enqueue(replyTo), cancellable)
      case RequestLimitExceeded =>
        cancellable.cancel()
        close(waitQueue, scheduleLimitExceeded(context))
      case Open =>
        cancellable.cancel()
        processNext(waitQueue, context)
    }
  }

  private def processNext(waitQueue: Queue[ActorRef[Message]], context: ActorContext[InternalCommand]): Behavior[InternalCommand] = {
    waitQueue.dequeueOption match {
      case None =>
        open()
      case Some((head, tail)) =>
        head ! MayPass
        close(tail, scheduleNextRequest(context))
    }
  }

  private def scheduleNextRequest(context: ActorContext[InternalCommand]) =
    context.scheduleOnce(requestTimeout, context.self, Open)

  private def scheduleLimitExceeded(context: ActorContext[InternalCommand]) =
    context.scheduleOnce(requestLimitExceededTimeout, context.self, Open)
}
