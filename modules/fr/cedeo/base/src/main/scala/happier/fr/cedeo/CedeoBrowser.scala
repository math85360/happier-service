package happier.fr.cedeo

import com.gargoylesoftware.htmlunit._
import com.iz2use.common.Filter.defaultFilter.{ prepare => filter }
import happier.api._
import happier.api.document._
import happier.api.WebpageFormatWasChanged
import io.circe.ACursor
import io.circe.Json
import io.circe.parser
import io.circe.syntax._
import java.net.URL
import java.time.format.{ DateTimeFormatter, DateTimeParseException }
import java.time.LocalDate
import model._
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser.HtmlUnitDocument
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import com.gargoylesoftware.htmlunit.util.NameValuePair
import io.circe.Decoder
import akka.Done

final case object CedeoBrowser extends SupplierBrowserBasedService {
  type NextPage = Int
  type Document = model.Document
  type Credentials = (String, String)

  override def createBrowser(): HtmlUnitBrowser = {
    val browser = new HtmlUnitBrowser()
    val options = browser.underlying.getOptions()
    options.setJavaScriptEnabled(false)
    browser
  }

  object DateFormat {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    def apply(in: String): LocalDate =
      LocalDate.parse(in.trim(), dateFormatter)

    def unapply(in: String): Option[LocalDate] =
      try { Some(apply(in)) }
      catch { case e: DateTimeParseException => None }
  }

  val serviceName = 'Cedeo
  private val baseUrl = "https://www.cedeo.fr"
  private val loginUrl = s"$baseUrl/connexion"
  private val endPointUrl = s"$baseUrl/sg_endpoint/query/"

  def attemptLogin(loginPageDocument: HtmlUnitBrowser.HtmlUnitDocument, credentials: Credentials, captcha: Option[String])(implicit browser: HtmlUnitBrowser) = {
    val formBuildId = loginPageDocument >> attr("value")("input[name='form_build_id']")
    browser.post(loginUrl, Map(
      "form_location" -> "",
      "form_build_id" -> formBuildId,
      "form_id" -> "sg_account_login_form",
      "login" -> credentials._1,
      "password" -> credentials._2,
      "op" -> "Connexion"))
  }

  override def login(credentials: Credentials, captchaSolver: java.net.URL => Future[String])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[Unit] = {
    for {
      loginPageDocument <- Future(browser.get(loginUrl))
      result <- Future(attemptLogin(loginPageDocument, credentials, None))
    } yield ()
  }

  // need to change browser : HtmlUnitBrowser to askBrowser: () => Future[HtmlUnitBrowser] --> use Throttler before making request
  override def downloadDocument(document: happier.fr.cedeo.model.Document)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[DownloadingStream] =
    for {
      url <- document match {
        case document: Invoice =>
          Future.successful(s"$baseUrl${document.downloadUrl}")
        case document: NotInvoice =>
          for {
            details <- makeRequestDocument(QueryType.OrderDetailsPdf, document.rawId)
          } yield details.data.url.url
      }
    } yield {
      val response = browser.get(url).window.getEnclosedPage().getWebResponse()
      DownloadingStream.fromBrowserResponse(response, document.id.concat(".pdf"))
    }

  val implementedDocumentCategories = QueryType.list
    .collect {
      case query: DocumentListQueryType =>
        query.documentCategory -> (query -> query.documentCategory)
    }
    .toMap

  def parseData[A](str: String)(implicit decodeA: Decoder[QueryResult[A]]): Try[QueryResult[A]] = for {
    json <- parser.parse(str).toTry
    response <- json.as(decodeA).toTry.recoverWith {
      case failure: io.circe.DecodingFailure =>
        Failure(ParseDataException(serviceName, json.hcursor.replay(failure.history.drop(1)).focus, failure))
    }
    if response.errors.isEmpty
  } yield response //.data -> listing.page.total

  def makeRequest[A](queryType: QueryType.Aux[A], parameters: List[(String, String)])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[QueryResult[A]] = {
    val req = new WebRequest(new java.net.URL(endPointUrl), "application/json")
    req.setHttpMethod(com.gargoylesoftware.htmlunit.HttpMethod.POST)
    req.setAdditionalHeader(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")
    req.setRequestParameters(parameters.map { case (k, v) => new NameValuePair(s"queries[${queryType.query}][input]$k", v) }.toBuffer.asJava)
    for {
      page <- Future(browser.underlying.getPage[Page](req).getWebResponse())
      str = page.getContentAsString()
      result <- Future.fromTry(parseData(str)(queryType.decoder))
    } yield result
  }

  def makeRequestDocument[A](queryType: QueryType.Aux[A], id: String)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext) = {
    makeRequest(queryType, List("[id]" -> id))
  }

  def makeListRequest[A](queryType: QueryType.Aux[A], currentPage: Int)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext) = {
    val parameters = {
      val query = Query(QueryInput(listing = ListingParameters(currentPage)))
      val searchPrefix = "[search]"
      val listingPrefix = "[listing]"
      List(
        searchPrefix -> query.input.search,
        s"$listingPrefix[page]" -> s"${query.input.listing.page}",
        s"$listingPrefix[itemsPerPage]" -> s"${query.input.listing.itemsPerPage}",
        s"$listingPrefix[sort]" -> s"${query.input.listing.sort}")
    }
    makeRequest(queryType, parameters).collect {
      case QueryResult(data, Some(listing), Nil, _) => data -> listing.page.total
    }
  }

  def retrieveParametizedPagedDocumentList[A <: Document, B <: DocumentCategory](category: B, currentPage: Int)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext, queryType: QueryType.Aux2[B, A]): Future[(List[A], Option[Int])] = {
    for {
      (results, maxPages) <- makeListRequest(queryType, currentPage)
    } yield results -> (if (currentPage < maxPages - 1) Some(currentPage + 1) else None)
  }

  override def retrievePagedDocumentList(category: DocumentCategory, page: Option[Int])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[(List[Document], Option[Int])] = {
    val currentPage = page.getOrElse(0)
    implementedDocumentCategories.get(category) match {
      case Some((query, category)) =>
        for {
          (results, maxPages) <- makeListRequest[query.Res](query, currentPage)
        } yield results -> (if (currentPage < maxPages - 1) Some(currentPage + 1) else None)
      case _ =>
        Future.failed(new DocumentCategoryUnhandled(serviceName, category))
    }
  }
}