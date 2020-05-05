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
import scala.concurrent.Future
import java.util.concurrent.TimeoutException
import akka.actor.FSM.State
import scala.concurrent.ExecutionContext

trait AccountDocumentSession extends NormalizedDocumentSession {
  type Stateful
  val documentSource: NormalizedDocumentSource.Aux[Command]

  sealed trait Command
  case class StartDocumentStream(params: documentSource.Params, replyTo: ActorRef[StreamStarted[documentSource.Command]]) extends Command
  private case object Connected extends Command
  private case class ConnectionFailure(throwable: Throwable) extends Command
  private case object ConnectionTimeout extends Command
  case class MakeOrder(replyTo: ActorRef[Unit]) extends Command
  case class MakeRequest[T](f: Stateful => Future[T], replyTo: ActorRef[RequestResponseResult[T]]) extends Command
  private case class RequestResult[T](request: MakeRequest[T], result: Try[T]) extends Command
  private case class ThrottlerAnswer[T](request: MakeRequest[T], failure: Option[Throwable]) extends Command
  case class RequestResponseResult[T](request: MakeRequest[T], response: Try[T])

  def makeOrder(replyTo: ActorRef[Unit]): Command = MakeOrder(replyTo)
  def makeRequest[T](f: Stateful => Future[T])(replyTo: ActorRef[RequestResponseResult[T]]): Command = MakeRequest(f, replyTo)

  def initialState(): Stateful
  def loginStage(params: Params, captchaSolver: java.net.URL => Future[String])(implicit state: Stateful, ec: ExecutionContext): Future[Unit]
  def captchaSolver: java.net.URL => Future[String] = _ => ???

  override final def apply(parent: ActorRef[ParentCommand], params: Params): Behavior[Command] = Behaviors.setup { context =>
    val throttlerRef = context.spawn(Behaviors.logMessages(Throttler(ThrottlerStrategy.AllowOnlyOneRequestByTimeUnit(1.second, 5.minutes))), "throttler")
    context.watch(throttlerRef)
    login(params)(parent, throttlerRef)
  }

  private def login(params: Params)(implicit parent: ActorRef[ParentCommand], throttler: Throttler.Ref): Behavior[Command] = Behaviors.withStash(100) { buffer =>
    Behaviors.setup { context =>
      implicit val state = initialState()
      implicit val ec = context.executionContext
      context.pipeToSelf(loginStage(params, captchaSolver) /*VmMateriauxBrowser.loginStage(params._1, params._2, url => {
        implicit val system = context.system
        val supervisor = (context.system.unsafeUpcast[SupervisorCommand])
        implicit val timeout = Timeout(2.minutes)
        for {
          CaptchaSolved(`url`, value) <- supervisor.ask(AskCaptcha(url, _))
        } yield value
        //context.self.ask(ref => AskCaptcha)
      })*/ ) {
        case Success(v) => Connected
        case Failure(exception) => ConnectionFailure(exception)
      }
      val timeout = context.scheduleOnce(3.minutes, context.self, ConnectionTimeout)
      pendingLogin(buffer, timeout)
    }
  }

  private def pendingLogin(buffer: StashBuffer[Command], timeout: Cancellable)(implicit ref: ActorRef[ParentCommand], state: Stateful, throttler: Throttler.Ref): Behavior[Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case Connected =>
          timeout.cancel()
          buffer.unstashAll(logged())
        case ConnectionFailure(reason) =>
          timeout.cancel()
          Behaviors.stopped
        case ConnectionTimeout =>
          Behaviors.stopped
        case cmd =>
          buffer.stash(cmd)
          Behaviors.same
      }
  }

  private def logged()(implicit parent: ActorRef[ParentCommand], state: Stateful, throttler: Throttler.Ref) = Behaviors.receive[Command] {
    (context, message) =>
      message match {
        case StartDocumentStream(params, replyTo) =>
          val ref = context.spawn(Behaviors.logMessages(documentSource(context.self, params)), "documentSource")
          context.watch(ref)
          replyTo ! StreamStarted(ref)
          Behaviors.same
        case req @ MakeRequest(f, replyTo) =>
          implicit val timeout = Timeout(10.minutes)
          context.ask(throttler, Throttler.WantToPass) {
            case Success(Throttler.MayPass) => ThrottlerAnswer(req, None)
            case Failure(throwable: TimeoutException) => ThrottlerAnswer(req, Some(throwable))
            case Failure(exception) => throw exception
          }
          Behaviors.same
        case ThrottlerAnswer(req @ MakeRequest(f, replyTo), failure) =>
          failure match {
            case None =>
              context.pipeToSelf(f(state)) { res => RequestResult(req, res) }
              Behaviors.same
            case Some(failure) =>
              replyTo ! RequestResponseResult(req, Failure(failure))
              Behaviors.same
          }
        case RequestResult(req @ MakeRequest(_, replyTo), result) =>
          replyTo ! RequestResponseResult(req, result)
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
  }
    .receiveSignal {
      case (context, Terminated(ref)) if ref != throttler =>
        Behaviors.same
    }
}