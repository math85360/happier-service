package happier.api

import com.gargoylesoftware.htmlunit.WebResponse
import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
import happier.api.document.DocumentCategory
import scala.concurrent.{ ExecutionContext, Future }
import java.io.InputStream

trait BrowserBasedService {
  def createBrowser(): HtmlUnitBrowser
}
trait SupplierBrowserBasedService extends BrowserBasedService {
  type NextPage
  type Document
  type Credentials
  implicit val serviceName: Symbol

  def login(credentials: Credentials, captchaSolver: java.net.URL => Future[String])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[Unit]

  def retrievePagedDocumentList(category: DocumentCategory, page: Option[NextPage])(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[(List[Document], Option[NextPage])]

  def downloadDocument(document: Document)(implicit browser: HtmlUnitBrowser, ec: ExecutionContext): Future[DownloadingStream]
}

sealed trait DownloadingStream
object DownloadingStream {
  final case object NotFound extends DownloadingStream
  final case class File(name: String, contentType: String, length: Long, stream: InputStream) extends DownloadingStream
  def fromBrowserResponse(response: WebResponse, defaultName: String): DownloadingStream = {
    val dispositions = Option(response.getResponseHeaderValue("Content-Disposition")).toSeq.flatMap(_.split(" *; *"))
    val name = dispositions.find(_.startsWith("filename"))
      .orElse(dispositions.find(_.startsWith("name")))
      .map(_.dropWhile(_ != '=').drop(1).stripPrefix("\"").stripSuffix("\""))
      .getOrElse(defaultName)
    DownloadingStream.File(name, response.getContentType, response.getContentLength, response.getContentAsStream)
  }
}