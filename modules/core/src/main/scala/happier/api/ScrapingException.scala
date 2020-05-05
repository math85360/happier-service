package happier.api

import happier.api.document.DocumentCategory
import io.circe.Json

trait ScrapingException { _: Exception =>
  def serviceName: Symbol
}
final case class WebpageFormatWasChanged(serviceName: Symbol, details: String, throwable: Throwable = None.orNull) extends Exception(s"$serviceName $details", throwable) with ScrapingException
final case class DocumentCategoryUnhandled(serviceName: Symbol, category: DocumentCategory) extends Exception(s"$serviceName $category unhandled by this service") with ScrapingException

final case class ParseDataException(serviceName: Symbol, json: Option[Json], throwable: Throwable) extends Exception(s"${throwable.getMessage()} for json starting with :\n${json.map(_.toString().take(2000))}", throwable) with ScrapingException