package happier.fr.vmmateriaux
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import happier.api._
import happier.actor._
import happier.api.document.DocumentCategory
import java.util.concurrent.TimeoutException
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.existentials
import scala.language.higherKinds
import scala.util.{ Failure, Success, Try }

object VmMateriauxAccountSession extends NormalizedDocumentSession[VmMateriauxService.Command] {
  final type Document = VmMateriauxBrowser.Document
  val Document = VmMateriauxBrowser.Document

  sealed trait Command
  final case class StartDocumentStream(params: VmMateriauxDocumentSource.Params, replyTo: ActorRef[StreamStarted[VmMateriauxDocumentSource.Command]]) extends Command
  private final case object Connected extends Command
  private final case class ConnectionFailure(throwable: Throwable) extends Command
  private final case object ConnectionTimeout extends Command
  final case class MakeOrder(replyTo: ActorRef[Unit]) extends Command
  final case class MakeRequest[T](f: HtmlUnitBrowser => Future[T], replyTo: ActorRef[RequestResponse[T]]) extends Command
  private final case class RequestResult[T](request: MakeRequest[T], result: Try[T]) extends Command
  private final case class ThrottlerAnswer[T](request: MakeRequest[T], failure: Option[Throwable]) extends Command
  sealed trait RequestResponse[+T]
  final case class RequestResponseSuccess[T](request: MakeRequest[T], response: T) extends RequestResponse[T]
  final case class RequestResponseFailure[T](request: MakeRequest[T], failure: Throwable) extends RequestResponse[T]

  def makeOrder(replyTo: ActorRef[Unit]): Command = MakeOrder(replyTo)
  def makeRequest[T](f: HtmlUnitBrowser => Future[T])(replyTo: ActorRef[RequestResponse[T]]): Command = MakeRequest(f, replyTo)

  final type Params = (VmMateriauxService.AccountNumber, String)

  override def apply(parent: ActorRef[VmMateriauxService.Command], params: Params): Behavior[Command] = Behaviors.setup { context =>
    val throttlerRef = context.spawn(Behaviors.logMessages(Throttler(ThrottlerStrategy.AllowOnlyOneRequestByTimeUnit(1.second, 5.minutes))), "throttler")
    context.watch(throttlerRef)
    login(params)(parent, throttlerRef)
  }

  private def login(params: Params)(implicit parent: ActorRef[VmMateriauxService.Command], throttler: Throttler.Ref): Behavior[Command] = Behaviors.withStash(100) { buffer =>
    Behaviors.setup { context =>
      implicit val browser = new HtmlUnitBrowser()
      implicit val ec = context.executionContext
      browser.underlying.getOptions().setJavaScriptEnabled(false)
      context.pipeToSelf(VmMateriauxBrowser.loginStage(params._1, params._2, url => {
        implicit val system = context.system
        val supervisor = (context.system.unsafeUpcast[SupervisorCommand])
        implicit val timeout = Timeout(2.minutes)
        for {
          CaptchaSolved(`url`, value) <- supervisor.ask(AskCaptcha(url, _))
        } yield value
        //context.self.ask(ref => AskCaptcha)
      })) {
        case Success(v) => Connected
        case Failure(exception) => ConnectionFailure(exception)
      }
      val timeout = context.scheduleOnce(3.minutes, context.self, ConnectionTimeout)
      pendingLogin(buffer, timeout)
    }
  }

  private def pendingLogin(buffer: StashBuffer[Command], timeout: Cancellable)(implicit ref: ActorRef[VmMateriauxService.Command], browser: HtmlUnitBrowser, throttler: Throttler.Ref): Behavior[Command] = Behaviors.receive {
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

  private def logged()(implicit parent: ActorRef[VmMateriauxService.Command], browser: HtmlUnitBrowser, throttler: Throttler.Ref) = Behaviors.receive[Command] {
    (context, message) =>
      message match {
        case StartDocumentStream(params, replyTo) =>
          val ref = context.spawn(Behaviors.logMessages(VmMateriauxDocumentSource(context.self, params)), "source")
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
              context.pipeToSelf(f(browser)) { res => RequestResult(req, res) }
              Behaviors.same
            case Some(failure) =>
              replyTo ! RequestResponseFailure(req, failure)
              Behaviors.same
          }
        case RequestResult(req @ MakeRequest(_, replyTo), result) =>
          val reply = result match {
            case Success(value) => RequestResponseSuccess(req, value)
            // catch if browser respond that website unknown to respond
            case Failure(throwable) => RequestResponseFailure(req, throwable)
          }
          replyTo ! reply
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