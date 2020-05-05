package happier.actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.api._
import scala.concurrent.duration._
import scala.util.Try
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure
import scala.collection.immutable.Queue

object PaginatedDocumentSource {
  sealed trait Command[Doc, Next]
  case class StreamAck[Doc, Next]() extends Command[Doc, Next]
  case class Init[Doc, Next](target: ActorRef[StreamSourceMessage[Doc]]) extends Command[Doc, Next]
  final case class Response[Doc, Next](result: Try[(List[Doc], Option[Next])]) extends Command[Doc, Next]
}

/**
 * Document Source that depends from paginated list
 */
trait PaginatedDocumentSource extends NormalizedDocumentSource {
  import PaginatedDocumentSource._
  final type Message = StreamSourceMessage[Out]
  type NextPage // Can be String or Int or ...
  final type Command = PaginatedDocumentSource.Command[Out, NextPage]

  private implicit def adaptResponse: Try[(List[Out], Option[NextPage])] => Command = PaginatedDocumentSource.Response(_)

  override final def apply(parent: ActorRef[ParentCommand], params: Params): Behavior[Command] =
    uninitialized(parent, params)

  private def uninitialized(implicit parent: ActorRef[ParentCommand], params: Params): Behavior[Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case Init(target) =>
          implicit val ref = target
          implicit val _context = context
          processRequest(None)
          active(Queue.empty, None, 1, false)(parent, params, target)
        case command =>
          throw new Exception(s"Unexpected $command in unitialized state")
      }
  }

  private def processAck(item: Out, queue: Queue[Out], nextPage: Option[NextPage], acks: Int, finished: Boolean)(implicit parent: ActorRef[ParentCommand], params: Params, ref: ActorRef[Message], context: ActorContext[Command]) = {
    context.log.debug("Item sent to Source")
    ref ! StreamSourceMessageWrapper(item)
    active(queue, nextPage, acks - 1, finished)
  }

  def processRequest(nextPage: Option[NextPage])(implicit asResponse: Try[(List[Out], Option[NextPage])] => Command, parent: ActorRef[ParentCommand], params: Params, context: ActorContext[Command]): Unit

  private def processComplete(queue: Queue[Out], nextPage: Option[NextPage], acks: Int, finished: Boolean)(implicit parent: ActorRef[ParentCommand], params: Params, ref: ActorRef[Message], context: ActorContext[Command]): Behavior[Command] = {
    queue.dequeueOption match {
      case None if finished && acks > 0 =>
        context.log.debug("Completed")
        ref ! StreamComplete()
        Behaviors.stopped
      case Some((head, queue)) if acks > 0 =>
        processAck(head, queue, nextPage, acks, finished)
      case _ =>
        context.log.debug(s"not completed ${queue.isEmpty}, ${!finished}")
        if (queue.isEmpty && !finished) processRequest(nextPage)
        active(queue, nextPage, acks, finished)
    }
  }

  private def active(queue: Queue[Out], nextPage: Option[NextPage], acks: Int, finished: Boolean)(implicit parent: ActorRef[ParentCommand], params: Params, ref: ActorRef[Message]): Behavior[Command] = Behaviors.receive { (_context, message) =>
    implicit val context = _context
    context.log.debug(s"ACTIVE : ${queue.size}, ${nextPage}, $acks, $finished")
    if (acks < 0) throw new Exception(s"ack count can not be < 0")
    message match {
      case StreamAck() =>
        context.log.debug(s"ACK RECEIVED")
        processComplete(queue, nextPage, acks + 1, finished)
      case Response(message) =>
        context.log.debug(s"RESPONSE RECEIVED with ${message.isSuccess}")
        message match {
          case Success((items, nextPage)) =>
            processComplete(queue.enqueue(items), nextPage, acks, nextPage.isEmpty)
          case Failure(throwable) =>
            context.log.error("ResponseFailure", throwable)
            throw new Exception("response failure while handling response in active", throwable)
        }
      case command =>
        context.log.error(s"unexpected $command in active")
        throw new Exception(s"Unexpected $command in active state")
    }
  }
}