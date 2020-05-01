package happier.fr.vmmateriaux
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.CompletionStrategy
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.util.Timeout
import happier.api._
import happier.actor._
import happier.api.document.DocumentCategory

object TestLogic {
  def apply(username: String, password: String)(implicit supervisor: ActorSystem[SupervisorCommand], timeout: Timeout) = {
    import supervisor.executionContext
    for {
      ServiceStarted(serviceRef) <- supervisor.ref.ask(StartService(VmMateriauxService))
      SessionStarted(sessionRef) <- serviceRef.ask(VmMateriauxService.StartAccountSession((username, password), _))
      StreamStarted(documentRef) <- sessionRef.ask(VmMateriauxAccountSession.StartDocumentStream(DocumentCategory.Invoice, _))
    } yield {
      ActorSource.actorRefWithBackpressure[VmMateriauxDocumentSource.Message, VmMateriauxDocumentSource.Command](
        documentRef,
        VmMateriauxDocumentSource.StreamControl(StreamAck()),
        { case StreamComplete() => CompletionStrategy.draining },
        { case StreamFail(ex) => ex })
        .collect { case happier.api.StreamSourceMessageWrapper(msg) => msg }
        .mapMaterializedValue { sourceRef =>
          documentRef ! VmMateriauxDocumentSource.Init(sourceRef)
          sourceRef
        }
    }
  }
}