package happier.fr.leboncoin

import java.net.URL
import java.util.UUID
import java.time._
import io.circe._
import io.circe.generic.extras._
import java.time._
import java.time.chrono._

package object model {

  import java.time.format.DateTimeFormatter
  implicit val config: Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("locationType")
  //implicit def encodeURL = Encoder[String].contramap((_: URL).toString)
  implicit def decodeURL = Decoder[String].map(new URL(_))

  object DateTimeFormat {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  }

  implicit val decodeInstant: Decoder[LocalDateTime] = Decoder[String].map(str => LocalDateTime.from(DateTimeFormat.dateTimeFormatter.parse(str)))

  val redialProps = """(?s)\s*window\.__REDIAL_PROPS__\s*=\s*(.*)""".r

  // https://www.leboncoin.fr/recherche/?category=9&locations=city_postalcode_coordinates&page=2
  // last SCRIPT when text starts with window.__REDIAL_PROPS__
  // drop until '[' or '{'
  // parse with circe
  // List[Option[Adview]] flatten
  // *** configure with snake case ***
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Owner(
    storeId: String,
    userId: String,
    `type`: String,
    name: String,
    siren: Option[String] = None)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Location(
    city: String,
    zipcode: String,
    lat: Double,
    lng: Double)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Images(
    thumbUrl: Option[String] = None,
    smallUrl: Option[String] = None,
    nbImages: Int,
    urls: List[URL] = Nil,
    urlsThumb: List[URL] = Nil,
    urlsLarge: List[URL] = Nil)
  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Attribute(
    key: String,
    value: String,
    keyLabel: Option[String],
    valueLabel: String,
    generic: Boolean)

  @ConfiguredJsonCodec(encodeOnly = true)
  final case class RequestCityWithPostalCode(
    text: String,
    context: List[Json] = Nil) {
    require(text.nonEmpty)
  }

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Coordinates(lat: Double, lng: Double, defaultRadius: Double)
  @ConfiguredJsonCodec(decodeOnly = true)
  sealed trait LocationArea

  final case class City(
    area: Coordinates,
    city: Option[String] = None,
    departmentId: String,
    label: String,
    //locationType: "city"
    regionId: String,
    zipcode: String) extends LocationArea {
    def asRequestParameter =
      s"${city.get}_${zipcode}_${area.lat}_${area.lng}"
  }
  final case class Department(
    countryId: String,
    departmentId: String,
    label: String,
    //locationType: department
    regionId: String) extends LocationArea

  final case class DepartmentNear(
    countryId: String,
    departmentId: String,
    label: String,
    //locationType: departmentNear,
    regionId: String) extends LocationArea

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Adview(
    listId: Long,
    firstPublicationDate: LocalDateTime,
    expirationDate: Option[LocalDateTime],
    indexDate: LocalDateTime,
    subject: String,
    url: String,
    price: List[Double],
    //priceCalendar: Json,
    images: Images,
    attributes: List[Attribute],
    location: Location,
    body: String,
    owner: Owner,
    hasPhone: Boolean)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class InitialData(req: Json, data: Data)

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class Data(maxPages: Int, ads: List[Adview])

  object FluxState {
    implicit val decodeFluxState: Decoder[FluxState] = {
      Decoder.decodeList(Decoder.decodeNone.either(Decoder[InitialData])).emap { lst =>
        lst.collect {
          case Right(v) => v
        } match {
          case head :: Nil => Right(FluxState(head))
          case _ => Left("Flux State is not in the right format")
        }
      }
    }
  }
  final case class FluxState(initialData: InitialData)
}
