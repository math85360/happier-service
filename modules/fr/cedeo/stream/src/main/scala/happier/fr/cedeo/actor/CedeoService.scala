package happier
package fr
package cedeo
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.actor._
import happier.api._
import scala.concurrent.duration._
import happier.actor.Throttler
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import java.net.URL
import scala.concurrent.{ ExecutionContext, Future }
import happier.api.document.DocumentCategory
import scala.util.Try
import akka.util.Timeout

// object CedeoService extends Norma

/*object CedeoService extends NormalizedService {
    override val name: Symbol = 'Cedeo

  override def apply(): Behavior[Command] = started(Map.empty.withDefault(key => AccountSessions(key, Nil)))

  sealed trait Command
  final case class StartAccountSession(params: CedeoAccountSession.Params, replyTo: ActorRef[SessionStarted[CedeoAccountSession.Command]]) extends Command

  type AccountNumber = String

  final case class AccountSessions(accountNumber: AccountNumber, sessions: List[ActorRef[CedeoAccountSession.Command]]) {
    def +(ref: ActorRef[CedeoAccountSession.Command]) = copy(sessions = ref :: sessions)
    def -(ref: ActorRef[_]) = copy(sessions = sessions.filterNot(_ == ref))
  }

  private def started(sessions: Map[AccountNumber, AccountSessions]): Behavior[Command] = Behaviors.receive[Command] {
    (context, message) =>
      message match {
        case StartAccountSession(params, replyTo) =>
          val ref = context.spawnAnonymous(Behaviors.logMessages((CedeoAccountSession(context.self, params))))
          replyTo ! SessionStarted(ref)
          val key = params._1
          started(sessions + (key -> (sessions(key) + ref)))
        case _ =>
          Behaviors.unhandled
      }
  }
    .receiveSignal {
      case (context, Terminated(ref)) =>
        sessions.find(p => p._2.sessions.contains(ref)) match {
          case None =>
            Behaviors.same
          case Some(value) =>
            val nextSessions = value._2 - ref
            if (nextSessions.sessions.isEmpty)
              started(sessions - value._1)
            else
              started(sessions + (value._1 -> nextSessions))
        }
    }

}

object CedeoAccountSession extends AccountDocumentSession {
  final type ParentCommand = CedeoService.Command
  final type Document = model.Document
  final type Params = (CedeoService.AccountNumber, String)
  final type Stateful = HtmlUnitBrowser

  val documentSource = CedeoAccountDocumentSource

  override def initialState() = CedeoBrowser.createBrowser()

  override def loginStage(params: (String, String), captchaSolver: URL => Future[String])(implicit state: HtmlUnitBrowser, ec: ExecutionContext): Future[Unit] = CedeoBrowser.loginStage(params._1, params._2)
}

object CedeoAccountDocumentSource extends PaginatedDocumentSource {
  final type ParentCommand = CedeoAccountSession.Command
  final type Document = CedeoAccountSession.Document
  final type Params = DocumentCategory
  final type NextPage = Int

  override def processRequest(nextPage: Option[Int])(implicit asResponse: Try[(List[happier.fr.cedeo.model.Document], Option[Int])] => PaginatedDocumentSource.Command[happier.fr.cedeo.model.Document, Int], parent: ActorRef[CedeoAccountSession.Command], params: DocumentCategory, context: ActorContext[PaginatedDocumentSource.Command[happier.fr.cedeo.model.Document, Int]]): Unit = {
    implicit val timeout = Timeout(5.minutes)
    context.ask(parent, CedeoAccountSession.makeRequest(browser => CedeoBrowser.retrieveNormalizedPagedDocumentList(params, nextPage.getOrElse(0))(browser, context.executionContext))) { tried =>
      asResponse(tried.flatMap(_.response))
    }
  }

}*/ 