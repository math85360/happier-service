package happier.fr.vmmateriaux
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.api._
import scala.concurrent.duration._

object VmMateriauxService extends NormalizedService {
  override val name: Symbol = 'VmMateriaux
  def apply() = started(Map.empty.withDefault(key => AccountSessions(key, Nil, Throttler(1, 1.second))))

  final type AccountNumber = String

  sealed trait Command
  final case class StartAccountSession(params: VmMateriauxAccountSession.Params, replyTo: ActorRef[SessionStarted[VmMateriauxAccountSession.Command]]) extends Command
  final case class Throttler(count: Int, per: FiniteDuration)

  final case class AccountSessions(accountNumber: AccountNumber, sessions: List[ActorRef[VmMateriauxAccountSession.Command]], throttler: Throttler) {
    def +(ref: ActorRef[VmMateriauxAccountSession.Command]) = copy(sessions = ref :: sessions)
    def -(ref: ActorRef[_]) = copy(sessions = sessions.filterNot(_ == ref))
  }

  private def started( /*globalThrottler: Throttler,*/ sessions: Map[AccountNumber, AccountSessions]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
    message match {
      case StartAccountSession(params, replyTo) =>
        val ref = context.spawn(Behaviors.logMessages(VmMateriauxAccountSession(context.self, params)), "account")
        context.watch(ref)
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
            val nextSessions = (value._2 - ref)
            if (nextSessions.sessions.isEmpty)
              started(sessions - value._1)
            else
              started(sessions + (value._1 -> nextSessions))
        }
    }

  def getDocumentCategories() = VmMateriauxBrowser.implementedDocumentCategories
}