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
import scala.concurrent.ExecutionContext

object VmMateriauxAccountSession extends AccountDocumentSession {
  final type ParentCommand = VmMateriauxService.Command
  final type Document = VmMateriauxBrowser.Document
  val Document = VmMateriauxBrowser.Document
  final type Stateful = HtmlUnitBrowser
  final type Params = (VmMateriauxService.AccountNumber, String)

  val documentSource = VmMateriauxDocumentSource

  def initialState(): Stateful =
    VmMateriauxBrowser.createBrowser()

  def loginStage(params: Params, captchaSolver: java.net.URL => Future[String])(implicit state: Stateful, ec: ExecutionContext): Future[Unit] =
    VmMateriauxBrowser.login(params, captchaSolver)
}