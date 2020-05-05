package happier.fr.leboncoin
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
import scala.util.Success
import happier.fr.leboncoin.model.City

object TestLogic {
  def apply(search: String)(implicit supervisor: ActorSystem[SupervisorCommand], timeout: Timeout) = {
    import supervisor.executionContext
    for {
      ServiceFound(serviceRef) <- supervisor.ref.ask(FindService(LeBonCoinService))
      LeBonCoinService.CityList(Success(lst)) <- serviceRef.ref.ask(LeBonCoinService.GetCityListFromSearch(search, _))
      head :: Nil = lst.collect {
        case city @ City(area, Some(_), departmentId, label, regionId, zipcode) => city
      }
      StreamStarted(adRef) <- serviceRef.ask(LeBonCoinService.StartAdStream(head, _))
    } yield {
      ActorSource.actorRefWithBackpressure[LeBonCoinAdStream.Message, LeBonCoinAdStream.Command](
        adRef,
        PaginatedDocumentSource.StreamAck(),
        { case StreamComplete() => CompletionStrategy.draining },
        { case StreamFail(ex) => ex })
        .collect { case happier.api.StreamSourceMessageWrapper(msg) => msg }
        .mapMaterializedValue { sourceRef =>
          adRef ! PaginatedDocumentSource.Init(sourceRef)
          sourceRef
        }
    }
  }
}