package happier.fr.leboncoin

import happier.api._
import io.circe.parser
import io.circe.syntax._
import model._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser.HtmlUnitDocument
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import io.circe.Json
import scala.util.{ Failure, Success, Try }
import com.gargoylesoftware.htmlunit.WebRequest
import com.gargoylesoftware.htmlunit.HttpMethod
import com.gargoylesoftware.htmlunit.WebResponse
import com.gargoylesoftware.htmlunit.UnexpectedPage
import com.gargoylesoftware.htmlunit.HttpHeader
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder
import com.gargoylesoftware.htmlunit.AbstractPage
import com.gargoylesoftware.htmlunit.Page
import io.circe.ACursor

final object LeBonCoinBrowser {
  private val baseUrl = "https://www.leboncoin.fr"
  private val baseApiUrl = "https://api.leboncoin.fr"
  val serviceName = 'leBonCoin

  val requestHeaders = List(
    HttpHeader.ACCEPT_LANGUAGE -> "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3",
    HttpHeader.DNT -> "1",
    HttpHeader.CONNECTION -> "keep-alive")

  def createBrowser(): HtmlUnitBrowser = {
    val browser = new HtmlUnitBrowser()
    val options = browser.underlying.getOptions()
    options.setJavaScriptEnabled(false)
    requestHeaders.foreach { case (k, v) => browser.underlying.addRequestHeader(k, v) }
    browser
  }

  def retrieveCities(request: RequestCityWithPostalCode)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[List[LocationArea]] = {
    val req = new WebRequest(new java.net.URL(s"$baseApiUrl/api/parrot/v3/complete/location"), "*/*")
    req.setHttpMethod(HttpMethod.POST)
    req.setAdditionalHeader(HttpHeader.CONTENT_TYPE, "application/json")
    //req.setAdditionalHeader(HttpHeader.ACCEPT, "*/*")
    req.setRequestBody(request.asJson.noSpaces)
    for {
      page <- Future(browser.underlying.getPage[Page](req).getWebResponse())
      results <- {
        val str = page.getContentAsString()
        println(str)
        if (page.getStatusCode() == 204) Future.successful(Nil)
        else Future.fromTry(parseAreaResponse(str))
      }
    } yield results
  }

  def parseAreaResponse(str: String) = for {
    json <- parser.parse(str).toTry
    results <- json.as[List[LocationArea]].toTry
  } yield results

  def retrieveAdList(city: City, currentPage: Int)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[(List[Adview], Option[Int])] = {
    require(city.city.nonEmpty)
    val request = new WebRequest(new java.net.URL(s"$baseUrl/recherche/?category=9&locations=${city.asRequestParameter}&page=${currentPage + 1}"))
    for {
      doc <- Future(browser.exec(request))
      (result, maxPages) <- scrapeAdList(doc).transform(identity, WebpageFormatWasChanged(serviceName, "ad list", _))
    } yield result -> (if (currentPage < maxPages - 1) Some(currentPage + 1) else None)
  }

  val initialData = """(?s)^\s*window\.__REDIAL_PROPS__\s*=\s*(.*)$""".r

  def scrapeAdList(doc: HtmlUnitDocument): Future[(List[Adview], Int)] = {
    val scripts = doc >> elementList("script") >> text
    scripts.collect { case initialData(data) => data } match {
      case head :: Nil =>
        Future.fromTry(parseData(head))
      case lst =>
        throw WebpageFormatWasChanged(serviceName, s"${lst.size} script matching ruler while retrieving ad list")
    }
  }

  def parseData(str: String): Try[(List[Adview], Int)] = for {
    json <- parser.parse(str).toTry
    fluxState <- json.as[FluxState].toTry.recoverWith {
      case failure: io.circe.DecodingFailure =>
        Failure(ParseDataException(serviceName, json.hcursor.replay(failure.history.drop(1)).focus, failure))
    }
  } yield fluxState.initialData.data.ads -> fluxState.initialData.data.maxPages
}
