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

object VmMateriauxDocumentSource extends PaginatedDocumentSource {
  final type ParentCommand = VmMateriauxAccountSession.Command
  final type Document = VmMateriauxAccountSession.Document
  final type Params = DocumentCategory
  final type NextPage = String

  override def processRequest(nextPage: Option[NextPage])(implicit asResponse: Try[(List[Out], Option[NextPage])] => Command, parent: ActorRef[ParentCommand], params: Params, context: ActorContext[PaginatedDocumentSource.Command[VmMateriauxBrowser.Document, NextPage]]): Unit = {
    implicit val timeout = Timeout(5.minutes)
    context.ask(parent, VmMateriauxAccountSession.makeRequest(browser => VmMateriauxBrowser.retrievePagedDocumentList(params, nextPage)(browser, context.executionContext))) { tried =>
      asResponse(tried.flatMap(_.response))
    }
  }
}
