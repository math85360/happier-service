package happier.fr.vmmateriaux

import com.iz2use.common.Filter.defaultFilter.{ prepare => filter }
import happier.api.document._
import java.net.URL
import java.time.format.{ DateTimeFormatter, DateTimeParseException }
import java.time.LocalDate
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser.HtmlUnitDocument
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
//import net.ruippeixotog.scalascraper.model._
import scala.concurrent.{ ExecutionContext, Future, Promise }

final object VmMateriauxBrowser {
  object DateFormat {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    def apply(in: String): LocalDate =
      LocalDate.parse(in.trim(), dateFormatter)

    def unapply(in: String): Option[LocalDate] =
      try { Some(apply(in)) }
      catch { case e: DateTimeParseException => None }
  }

  val serviceName = "VM Materiaux"
  private val baseUrl = "https://www.vm-materiaux.fr"
  private val customerBaseUrl = s"$baseUrl/customer/account"
  private val salesBaseUrl = s"$baseUrl/sales"
  def login(loginPageDocument: HtmlUnitBrowser.HtmlUnitDocument, username: String, password: String, captcha: Option[String])(implicit browser: HtmlUnitBrowser) = {
    val formKeyInput = loginPageDocument >> element("input[name='form_key']")
    browser.post(s"$customerBaseUrl/loginPost/", captcha.foldLeft(Map(
      "form_key" -> formKeyInput.attr("value"),
      "login[username]" -> username,
      "login[password]" -> password,
      "send" -> ""))((map, captcha) => map + ("captcha[user_login]" -> captcha)))
  }

  def loginStage(username: String, password: String, askCaptcha: URL => Future[String])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[Unit] = {
    val p = Promise[Unit]
    val loginPageDocument = browser.get(s"$customerBaseUrl/login/")
    browser.underlying.waitForBackgroundJavaScriptStartingBefore(500)
    val firstAttemptLoginDocument = login(loginPageDocument, username, password, None)
    browser.underlying.waitForBackgroundJavaScriptStartingBefore(500)
    firstAttemptLoginDocument >?> attr("src")("img.captcha-img") match {
      case Some(captchaImgSource) =>
        p.completeWith {
          askCaptcha(new URL(captchaImgSource)).map { code =>
            login(firstAttemptLoginDocument, username, password, Some(code))
            ()
          }
        }
      case None =>
        p.success(())
    }
    p.future
  }

  private val mapDocumentCategory: Map[DocumentCategory, String] = Map(
    DocumentCategory.Invoice -> "invoice",
    DocumentCategory.Quote -> "quote",
    DocumentCategory.Order -> "order")

  val implementedDocumentCategories = mapDocumentCategory.keySet

  def retrievePagedDocumentList(category: DocumentCategory, currentPage: Option[String])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[(List[Document], Option[String])] = {
    mapDocumentCategory.get(category) match {
      case None => Future.failed(DocumentCategoryNotHandledException(serviceName, category))
      case Some(part) =>
        val url = currentPage.getOrElse(s"$salesBaseUrl/$part/history/")
        Future(browser.get(url)).flatMap { doc =>
          scrapeDocumentListPage(doc) match {
            case None => Future.failed(WebpageFormatWasChanged(serviceName, category))
            case Some(value) => Future.successful(value)
          }
        }
    }
  }

  final case class Document(officeName: String, date: LocalDate, reference: String, downloadLink: URL)

  def downloadDocument(document: Document)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext) = Future {
    val response = browser.get(document.downloadLink.toString).window.getEnclosedPage.getWebResponse()
    val dispositions = Option(response.getResponseHeaderValue("Content-Disposition")).toSeq.flatMap(_.split(" *; *"))
    val name = dispositions.find(_.startsWith("filename"))
      .orElse(dispositions.find(_.startsWith("name")))
      .map(_.dropWhile(_ != '=').drop(1).stripPrefix("\"").stripSuffix("\""))
      .getOrElse(document.reference.concat(".pdf"))
    (name, response.getContentType, response.getContentLength, response.getContentAsStream)
  }

  def scrapeDocumentListPage(doc: HtmlUnitDocument): Option[(List[Document], Option[String])] =
    for { table <- doc >?> element(".data-table") } yield {
      val headers = (table >> elementList("thead>tr>*") >> text).map(filter)
      val refField = headers.indexWhere(_.contains("reference"))
      val officeField = headers.indexWhere(x => x.contains("point") && x.contains("vente"))
      val dateField = headers.indexWhere(x => x.contains("date") || x.contains("enregistre"))
      val entries = (table >> elementList("tbody>tr")).filter(row => (row >?> attr("href")("a.btn-pdf")).isDefined).map { row =>
        val fields = row >> elementList("*") >> text
        val officeName = filter(fields(officeField))
        val date = DateFormat(fields(dateField))
        val ref = fields(refField)
        val downloadLink = new URL(row >> attr("href")("a.btn-pdf"))
        Document(officeName, date, ref, downloadLink)
      }
      val nextUrlOpt = doc >?> attr("href")("a.next")
      (entries, nextUrlOpt)
    }
}