package happier.api

package object document {

  sealed abstract class DocumentCategory extends Product with Serializable
  object DocumentCategory {
    case object Invoice extends DocumentCategory
    case object CreditInvoice extends DocumentCategory
    case object DeliveryForm extends DocumentCategory
    case object Order extends DocumentCategory
    case object Quote extends DocumentCategory
    case object PriceOffer extends DocumentCategory
    case object Certificate extends DocumentCategory
    case object Statement extends DocumentCategory
  }

  final case class DocumentCategoryNotHandledException(serviceName: Symbol, category: DocumentCategory) extends RuntimeException(s"Document Category $category not handled by $serviceName")
}
