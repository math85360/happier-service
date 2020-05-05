package happier
package fr
package leboncoin
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.api._
import model._
import scala.concurrent.duration._
import scala.util.Try
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure
import scala.collection.immutable.Queue
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import scala.concurrent.Future
import happier.actor.Throttler
import happier.actor.ThrottlerStrategy
import java.util.concurrent.TimeoutException

object LeBonCoinService extends NormalizedService {
  override val name: Symbol = 'LeBonCoin

  sealed trait Command
  final case class MakeRequest[T](f: HtmlUnitBrowser => Future[T], replyTo: ActorRef[RequestResponseResult[T]]) extends Command
  final case class RequestResponseResult[T](request: MakeRequest[T], response: Try[T])
  final case class StartAdStream(params: LeBonCoinAdStream.Params, replyTo: ActorRef[StreamStarted[LeBonCoinAdStream.Command]]) extends Command
  private final case class RequestResult[T](request: MakeRequest[T], result: Try[T]) extends Command
  private final case class ThrottleAnswer[T](request: MakeRequest[T], failure: Option[Throwable]) extends Command
  final case class GetCityListFromSearch(search: String, replyTo: ActorRef[CityList]) extends Command
  final case class GetCityListFromSearchResult(result: CityList, replyTo: ActorRef[CityList]) extends Command
  final case class CityList(result: Try[List[LocationArea]])

  def makeRequest[T](f: HtmlUnitBrowser => Future[T])(replyTo: ActorRef[RequestResponseResult[T]]): MakeRequest[T] = MakeRequest(f, replyTo)

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    val throttlerRef = context.spawn(Behaviors.logMessages(Throttler(ThrottlerStrategy.AllowOnlyOneRequestByTimeUnit(3.seconds, 30.minutes))), "throttler")
    context.watch(throttlerRef)
    active()(throttlerRef, LeBonCoinBrowser.createBrowser())
  }

  def active()(implicit throttler: Throttler.Ref, browser: HtmlUnitBrowser): Behavior[Command] = Behaviors.receive[Command] {
    (context, message) =>
      message match {
        case StartAdStream(params, replyTo) =>
          val ref = context.spawn(LeBonCoinAdStream(context.self, params), "ad-source")
          context.watch(ref)
          replyTo ! StreamStarted(ref)
          Behaviors.same
        case GetCityListFromSearch(search, replyTo) =>
          implicit val timeout = Timeout(10.minutes)
          context.ask(context.self, makeRequest(browser => LeBonCoinBrowser.retrieveCities(RequestCityWithPostalCode(search))(browser, context.executionContext))(_)) { tried =>
            GetCityListFromSearchResult(CityList(for {
              result <- tried
              items <- result.response
            } yield items), replyTo)
          }
          Behaviors.same
        case GetCityListFromSearchResult(result, replyTo) =>
          replyTo ! result
          Behaviors.same
        case req @ MakeRequest(f, replyTo) =>
          implicit val timeout = Timeout(10.minutes)
          context.ask(throttler, Throttler.WantToPass) {
            case Success(Throttler.MayPass) => ThrottleAnswer(req, None)
            case Failure(throwable: TimeoutException) => ThrottleAnswer(req, Some(throwable))
            case Failure(exception) => throw exception
          }
          Behaviors.same
        case ThrottleAnswer(req @ MakeRequest(f, replyTo), failure) =>
          failure match {
            case None =>
              context.pipeToSelf(f(browser)) { res => RequestResult(req, res) }
            case Some(failure) =>
              replyTo ! RequestResponseResult(req, Failure(failure))
          }
          Behaviors.same
        case RequestResult(req @ MakeRequest(_, replyTo), result) =>
          replyTo ! RequestResponseResult(req, result)
          Behaviors.same
        case command =>
          Behaviors.unhandled
      }
  }
    .receiveSignal {
      case (context, Terminated(ref)) if ref != throttler =>
        Behaviors.same
    }
}

