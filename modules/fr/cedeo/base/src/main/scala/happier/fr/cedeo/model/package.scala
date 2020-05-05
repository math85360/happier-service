package happier.fr.cedeo

import java.net.URL
import java.util.UUID
import java.time._
import io.circe._
import io.circe.generic.extras._
import java.time._
import java.time.chrono._

package object model {

  import com.iz2use.common.ListOfSealed

  import happier.api.document.DocumentCategory

  import java.time.format.DateTimeFormatter
  implicit val config: Configuration = Configuration.default.withDefaults
  sealed trait QueryType {
    def query: String
    type Res
    def decoder: Decoder[QueryResult[Res]]

  }
  sealed abstract class DocumentQueryType[R](val query: String)(implicit decodeResponse: Decoder[QueryResult[R]]) extends QueryType {
    final type Res = R
    final def decoder: Decoder[QueryResult[R]] = decodeFromQueryType(this)
  }
  sealed abstract class DocumentListQueryType extends QueryType {
    def documentCategory: DocumentCategory
    type Cat
    type Doc <: model.Document
    final type Res = List[Doc]
  }
  sealed abstract class AbstractDocumentListQueryType[R <: Document, C <: DocumentCategory](val query: String, val documentCategory: C)(implicit decodeResponse: Decoder[QueryResult[List[R]]]) extends DocumentListQueryType {
    final type Doc = R
    final type Cat = C
    final def decoder = decodeFromQueryType(this)
  }
  final object QueryType {
    type Aux[Res0] = QueryType {
      type Res = Res0
    }
    type Aux2[Cat0, Doc0 <: Document] = DocumentListQueryType {
      type Doc = Doc0
      type Cat = Cat0
    }

    implicit final case object InvoiceList extends AbstractDocumentListQueryType[Invoice, DocumentCategory.Invoice.type]("get:account.invoicesFactureList", DocumentCategory.Invoice)
    implicit final case object CreditInvoiceList extends AbstractDocumentListQueryType[Invoice, DocumentCategory.CreditInvoice.type]("get:account.invoicesAvoirList", DocumentCategory.CreditInvoice)
    implicit final case object DeliveryList extends AbstractDocumentListQueryType[NotInvoice, DocumentCategory.DeliveryForm.type]("get:account.deliveryNotesList", DocumentCategory.DeliveryForm)
    implicit final case object OrderList extends AbstractDocumentListQueryType[NotInvoice, DocumentCategory.Order.type]("get:account.ordersList", DocumentCategory.Order)
    implicit final case object QuoteList extends AbstractDocumentListQueryType[NotInvoice, DocumentCategory.Quote.type]("get:account.quotesList", DocumentCategory.Quote)
    implicit final case object OrderItem extends DocumentQueryType[Order]("get:account.order")
    implicit final case object OrderDetailsPdf extends DocumentQueryType[OrderDetailsPdfDownload]("get:account.orderDetailsPdfDownload")
    val list = ListOfSealed[QueryType]
  }

  final case class Query(input: QueryInput)
  final case class QueryInput(search: String = "", listing: ListingParameters)
  final case class ListingParameters(page: Int, itemsPerPage: Int = 0, sort: String = "")

  def decodeFromQueryType[R](queryType: QueryType)(implicit decodeR: Decoder[QueryResult[R]]): Decoder[QueryResult[R]] = decodeR.prepare {
    cursor =>
      cursor.downField(queryType.query)
  }

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class QueryResult[T](
    data: T,
    listing: Option[QueryResultListing] = None,
    errors: List[Json],
    messages: QueryResultMessages)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class QueryResultListing(itemsTotal: Int, page: QueryResultListingPage)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class QueryResultListingPage(current: Int, total: Int)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class QueryResultMessages(error: List[Json], success: List[Json])

  val localDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  implicit val decodeLocalDate = Decoder[String].map { str => LocalDate.from(localDateFormatter.parse(str)) }

  implicit val decodeDouble = Decoder[String].map { str =>
    str.replaceAllLiterally(",", ".").toDouble
  }.or(Decoder.decodeDouble)

  object UrlPath {
    implicit val decodePathChunk = Decoder.decodeString.map(UrlPath(_))
  }
  final case class UrlPath(val path: String) extends AnyVal
  object Url {
    implicit val decodeUrl = Decoder.decodeString.map(Url(_))
  }
  final case class Url(val url: String) extends AnyVal

  // Warning all fields of any items are string except booleans
  // Need to transform from String to right type before decoding
  sealed trait Document {
    def id: String
  }
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Invoice(
    agency: String,
    amount: Double,
    date: LocalDate,
    downloadUrl: String,
    id: String,
    number: String,
    payment: String,
    `type`: String // FACTURE
  ) extends Document

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class NotInvoice(
    agencyLabel: String,
    agencyUrl: String,
    deliveryDate: Option[LocalDate] = None,
    eligible: Boolean,
    expiryDate: Option[LocalDate] = None,
    id: String,
    orderDate: LocalDate,
    paymentUrl: Option[String] = None,
    rawId: String,
    refCode: String,
    refInternet: String,
    refOrder: String,
    refSite: String,
    status: String,
    totalPrice: Double,
    trackingParcels: List[Json],
    `type`: String // [empty]
  ) extends Document {
    def agency = agencyLabel
  }

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderDetailProductImage(
    description: String,
    src: Url)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderDetailProduct(
    alias: String,
    brandName: String,
    mainImage: OrderDetailProductImage,
    name: String,
    ref: String)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderDetailPrices(
    ecoTax: Double,
    totalPrice: Double,
    ufUnit: String,
    unitPrice: Double)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderDetail(
    id: String,
    pallet: Json,
    portfolio: String,
    prices: OrderDetailPrices,
    product: Json,
    promotion: Json,
    quantity: Double,
    stock: Json,
    `type`: String, // product
    weight: Double // real double
  )
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderPrices(
    htPrice: Double)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Order(
    address: Json,
    agencyAddress: Json,
    agencyLabel: String,
    agencyUrl: UrlPath,
    comments: List[Json],
    coordinates: Json,
    deliveryDate: String, // le <span>dd/mm/yyyy</span>
    deliveryLabel: String,
    deliveryType: String,
    eligible: Boolean,
    expiryDate: Option[LocalDate] = None,
    id: String,
    items: List[OrderDetail],
    locationUrl: Url,
    orderDate: LocalDate,
    paymentUrl: String,
    prices: OrderPrices,
    refCode: String,
    refInternet: String,
    refOrder: String,
    refSite: String,
    status: Json,
    trackingParcels: List[Json],
    `type`: String // internet
  )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class OrderDetailsPdfDownload(
    name: String,
    url: Url)
}
