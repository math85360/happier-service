package happier
package fr
package leboncoin
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import happier.actor._
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

object LeBonCoinAdStream extends PaginatedDocumentSource {
  type ParentCommand = LeBonCoinService.Command
  type Document = Adview
  type Params = City
  type NextPage = Int

  def processRequest(nextPage: Option[Int])(implicit asResponse: Try[(List[Out], Option[NextPage])] => Command, parent: ActorRef[ParentCommand], params: happier.fr.leboncoin.model.City, context: ActorContext[PaginatedDocumentSource.Command[happier.fr.leboncoin.model.Adview, Int]]): Unit = {
    implicit val timeout = Timeout(5.minutes)
    val page = nextPage.getOrElse(0)
    context.ask(parent, LeBonCoinService.makeRequest(browser => LeBonCoinBrowser.retrieveAdList(params, page)(browser, context.executionContext))) {
      tried =>
        asResponse {
          for {
            result <- tried
            items <- result.response
          } yield items
        }
    }
  }
}
