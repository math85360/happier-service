package happier.fr.vmmateriaux
package actor

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.util.Timeout
import happier.api._
import happier.actor._
import happier.api.document.DocumentCategory
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object VmMateriauxDocumentSource extends NormalizedDocumentSource[VmMateriauxAccountSession.Command, VmMateriauxAccountSession.Document] {
  type Params = DocumentCategory
  final type Message = StreamSourceMessage[Out]
  sealed trait Command
  case class StreamControl(command: StreamSourceCommand[Out]) extends Command
  case class Init(target: ActorRef[Message]) extends Command
  sealed trait ResponseCommand extends Command
  final case class ResponseSuccess(res: (List[Out], Option[String])) extends ResponseCommand
  final case class ResponseFailure(throwable: Throwable) extends ResponseCommand

  override def apply(parent: ActorRef[VmMateriauxAccountSession.Command], params: Params) = uninitialized(parent, params)

  private def uninitialized(implicit parent: ActorRef[VmMateriauxAccountSession.Command], params: Params): Behavior[Command] = Behaviors.receive { (context, message) =>
    context.log.debug(s"Unitialized")
    message match {
      case Init(ref) =>
        context.self ! StreamControl(StreamAck())
        active(Queue.empty, None, 0, false)(parent, params, ref.unsafeUpcast)
      case command =>
        throw new Exception(s"Unexpected $command in unitialized state")
    }
  }

  private def processAck(item: Out, queue: Queue[Out], nextPage: Option[String], acks: Int, finished: Boolean)(implicit parent: ActorRef[VmMateriauxAccountSession.Command], params: Params, ref: ActorRef[StreamSourceMessage[Out]], context: ActorContext[Command]) = {
    context.log.debug("Item sent to Source")
    ref ! StreamSourceMessageWrapper(item)
    active(queue, nextPage, acks - 1, finished)
  }

  private def processRequest(nextPage: Option[String])(implicit parent: ActorRef[VmMateriauxAccountSession.Command], params: Params, ref: ActorRef[Message], context: ActorContext[Command]): Unit = {
    context.log.debug("Request")
    implicit val timeout = Timeout(5.minutes)
    context.ask(parent, VmMateriauxAccountSession.makeRequest(browser => VmMateriauxBrowser.retrievePagedDocumentList(params, nextPage)(browser, context.executionContext))) {
      case Success(res) =>
        res match {
          case VmMateriauxAccountSession.RequestResponseSuccess(_, response) => ResponseSuccess(response)
          case VmMateriauxAccountSession.RequestResponseFailure(_, throwable) => ResponseFailure(throwable)
        }
      case Failure(throwable) => ResponseFailure(throwable)
    }
  }

  private def processComplete(queue: Queue[Out], nextPage: Option[String], acks: Int, finished: Boolean)(default: => Behavior[Command])(implicit ref: ActorRef[Message], context: ActorContext[Command]): Behavior[Command] = {
    if (finished && queue.isEmpty && nextPage.isEmpty && acks > 0) {
      context.log.debug("Completed")
      ref ! StreamComplete()
      Behaviors.stopped
    } else {
      default
    }
  }

  private def active(queue: Queue[Out], nextPage: Option[String], acks: Int, finished: Boolean)(implicit parent: ActorRef[VmMateriauxAccountSession.Command], params: Params, ref: ActorRef[Message]): Behavior[Command] = Behaviors.receive { (_context, message) =>
    implicit val context = _context
    context.log.debug(s"ACTIVE : ${queue.size}, ${nextPage}, $acks, $finished")
    if (acks < 0) throw new Exception(s"ack count can not be < 0")
    message match {
      case StreamControl(StreamAck()) =>
        processComplete(queue, nextPage, acks, finished) {
          queue.dequeueOption match {
            case None =>
              processRequest(nextPage)
              active(queue, nextPage, acks + 1, finished)
            case Some((item, nextQueue)) =>
              processAck(item, nextQueue, nextPage, acks + 1, finished)
          }
        }
      case ResponseSuccess(res) =>
        val finished = res._2.isEmpty
        processComplete(queue, res._2, acks, finished) {
          res._1 match {
            case head :: tl if acks > 0 =>
              processAck(head, queue.enqueue(tl), res._2, acks, finished)
            case _ =>
              active(queue.enqueue(res._1), res._2, acks, finished)
          }
        }
      case ResponseFailure(throwable) =>
        context.log.error("ResponseFailure", throwable)
        throw new Exception("response failure while handling response in active", throwable)
      case command =>
        context.log.error(s"unexpected $command in active")
        throw new Exception(s"Unexpected $command in active state")
    }
  }
}