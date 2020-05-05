package happier.fr.vmmateriaux

import com.iz2use.common.Filter.defaultFilter.{ prepare => filter }
import happier.api._
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
import happier.api.WebpageFormatWasChanged

final object VmMateriauxBrowser extends SupplierBrowserBasedService {
  type NextPage = String
  final case class Document(officeName: String, date: LocalDate, reference: String, downloadLink: URL)
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

  val serviceName = 'VmMateriaux
  private val baseUrl = "https://www.vm-materiaux.fr"
  private val customerBaseUrl = s"$baseUrl/customer/account"
  private val salesBaseUrl = s"$baseUrl/sales"

  def attemptLogin(loginPageDocument: HtmlUnitBrowser.HtmlUnitDocument, credentials: Credentials, captcha: Option[String])(implicit browser: HtmlUnitBrowser) = {
    val formKeyInput = loginPageDocument >> element("input[name='form_key']")
    browser.post(s"$customerBaseUrl/loginPost/", captcha.foldLeft(Map(
      "form_key" -> formKeyInput.attr("value"),
      "login[username]" -> credentials._1,
      "login[password]" -> credentials._2,
      "send" -> ""))((map, captcha) => map + ("captcha[user_login]" -> captcha)))
  }

  def login(credentials: Credentials, askCaptcha: URL => Future[String])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[Unit] = {
    val p = Promise[Unit]
    val loginPageDocument = browser.get(s"$customerBaseUrl/login/")
    //browser.underlying.waitForBackgroundJavaScriptStartingBefore(500)
    val firstAttemptLoginDocument = attemptLogin(loginPageDocument, credentials, None)
    //browser.underlying.waitForBackgroundJavaScriptStartingBefore(500)
    firstAttemptLoginDocument >?> attr("src")("img.captcha-img") match {
      case Some(captchaImgSource) =>
        p.completeWith {
          askCaptcha(new URL(captchaImgSource)).map { code =>
            attemptLogin(firstAttemptLoginDocument, credentials, Some(code))
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
            case None => Future.failed(DocumentCategoryUnhandled(serviceName, category))
            case Some(value) => Future.successful(value)
          }
        }
    }
  }

  def downloadDocument(document: Document)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext) = Future {
    DownloadingStream.fromBrowserResponse(
      browser.get(document.downloadLink.toString).window.getEnclosedPage.getWebResponse(),
      document.reference.concat(".pdf"))
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