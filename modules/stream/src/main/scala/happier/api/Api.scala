package happier.api

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.annotation.DoNotInherit
import akka.stream.ActorMaterializer
import akka.stream.CompletionStrategy
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.util.ByteString
import akka.util.Timeout
import scala.language.existentials

trait NormalizedService {
  type Command
  val name: Symbol
  def apply(): Behavior[Command]
}
object NormalizedService {
  type Aux[Command0] = NormalizedService {
    type Command = Command0
  }
}
trait NormalizedSession {
  type ParentCommand
  type Command
  type Params
  def apply(parent: ActorRef[ParentCommand], params: Params): Behavior[Command]
}
trait NormalizedDocumentSession extends NormalizedSession {
  type Document
}

trait NormalizedStream {
  type ParentCommand
  type Command
  type Params
  def apply(parent: ActorRef[ParentCommand], params: Params): Behavior[Command]
}

trait NormalizedSource extends NormalizedStream {
  type Out
}

object NormalizedDocumentSource {
  type Aux[ParentCommand0] = NormalizedDocumentSource {
    type ParentCommand = ParentCommand0
  }
}
trait NormalizedDocumentSource extends NormalizedSource {
  type Document
  final type Out = Document
}

trait NormalizedSink extends NormalizedStream {
  type ParentCommand
  type In
}

sealed trait StreamSinkCommand[T]
sealed trait StreamSourceMessage[T]
sealed trait StreamSinkMessage

final case class StreamSourceMessageWrapper[T](msg: T) extends StreamSourceMessage[T]
//final case class StreamSourceInit[T](ref: ActorRef[T]) extends StreamSourceCommand[T]

//final case class StreamSinkMessageWrapper[+T ](ackTo: ActorRef[StreamSinkMessage], msg: T#In) extends StreamSinkCommand[T]
final case class StreamSinkInit[T](ackTo: ActorRef[StreamSinkMessage]) extends StreamSinkCommand[T]

final case object StreamAck extends StreamSinkMessage
final case class StreamComplete[T]() extends StreamSourceMessage[T] with StreamSinkCommand[T]
final case class StreamFail[T](ex: Throwable) extends StreamSourceMessage[T] with StreamSinkCommand[T]

sealed trait SupervisorCommand
case class AskCaptcha(url: java.net.URL, replyTo: ActorRef[CaptchaSolved]) extends SupervisorCommand
case class CaptchaSolved(url: java.net.URL, value: String) extends SupervisorCommand
abstract class StartBehavior extends SupervisorCommand {
  type Command
  def apply(context: ActorContext[_]): Unit
}
object StartBehavior {
  type Aux[T] = StartBehavior {
    type Command = T
  }
  def apply[T](behavior: Behavior[T], replyTo: ActorRef[BehaviorStarted[T]]): StartBehavior.Aux[T] = new StartBehavior {
    final type Command = T
    override def apply(context: ActorContext[_]): Unit = {
      val ref = context.spawnAnonymous(behavior)
      context.watch(ref)
      replyTo ! BehaviorStarted(ref)
    }
  }
}
final case class BehaviorStarted[T](ref: ActorRef[T])

abstract class FindService extends SupervisorCommand {
  type TargetCommand
  val service: NormalizedService.Aux[TargetCommand]
  def replyTo: ActorRef[ServiceFound[TargetCommand]]
  final def apply(context: ActorContext[SupervisorCommand]): ActorRef[TargetCommand] = {
    val ref = context.child(service.name.toString()).map(_.asInstanceOf[ActorRef[TargetCommand]]).getOrElse {
      val ref = context.spawn(service(), service.name.toString)
      context.watch(ref)
      ref
    }
    replyTo ! ServiceFound(ref)
    ref
  }
}
object FindService {
  def apply[SrvCmd](_service: NormalizedService.Aux[SrvCmd]) = {
    final case class ConcreteStartService(replyTo: ActorRef[ServiceFound[SrvCmd]]) extends FindService {
      final type TargetCommand = SrvCmd
      override val service = _service
    }
    ConcreteStartService.apply _
  }
}
final case class ServiceFound[T](ref: ActorRef[T])

final case class SessionStarted[SessionCommand](ref: ActorRef[SessionCommand])
final case class StreamStarted[StreamCommand](ref: ActorRef[StreamCommand])
