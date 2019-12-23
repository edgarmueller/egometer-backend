package models

import java.time.Instant
import java.util.UUID

import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import models.auth.{SignInInfo, _}
import models.entry.{MeterEntriesByMeterDto, MeterEntriesEnvelopeDto, MeterEntry, MeterEntryDto}
import models.meter.{Meter, MeterDto, MeterEntryUpdate}
import models.schema.{Schema, SchemaDto}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.i18n.Lang
import play.api.libs.json.Reads._
import reactivemongo.play.json._
import play.api.libs.functional.syntax._

import scala.util.{Failure, Success, Try}

object JsonFormats{
  import play.api.libs.json._

  private val DateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val jodaDateReads: Reads[DateTime] = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(DateFormat))
    )
  )


  implicit val settingsFormat: OFormat[Settings] = Json.format[Settings]
  implicit val registrationFormat: OFormat[Registration] = Json.format[Registration]
  implicit val userFormat: OFormat[User] = Json.format[User]
  implicit val jodaDateWrites: Writes[DateTime] = (d: DateTime) => JsString(d.toString())
  implicit val meterFormat: OFormat[Meter] = Json.format[Meter]
  implicit val meterEntryFormat: OFormat[MeterEntry] = Json.format[MeterEntry]
  implicit val meterEntryUpdateFormat: OFormat[MeterEntryUpdate] = Json.format[MeterEntryUpdate]
  implicit val meterSchemaFormat: OFormat[Schema] = Json.format[Schema]
  implicit val meterEntryDtoFormat: OFormat[MeterEntryDto] = Json.format[MeterEntryDto]
  implicit val meterDtoFormat: OFormat[MeterDto] = Json.format[MeterDto]
  implicit val meterEntriesByMeterDtoFormat: OFormat[MeterEntriesByMeterDto] = Json.format[MeterEntriesByMeterDto]
  implicit val meterEntriesEnvelopDtoFormat: OFormat[MeterEntriesEnvelopeDto] = Json.format[MeterEntriesEnvelopeDto]
  implicit val schemaDtoFormat: OFormat[SchemaDto] = Json.format[SchemaDto]

  implicit val meterErrorWrites: OWrites[ErrorResponse] = (error: ErrorResponse) => Json.obj(
    "msgs" -> error.msgs,
    "errors" -> JsError.toJson(error.jsError)
  )

  implicit val tokenFormat: OFormat[SignInToken] = Json.format[SignInToken]

  implicit val emailAddressFormat: OFormat[EmailAddress] = new OFormat[EmailAddress] {
    val emailReads: Reads[EmailAddress] = (__ \ "email").read[String](email).map(EmailAddress.apply)
    val emailWrites: OWrites[EmailAddress] = Json.writes[EmailAddress]

    override def writes(email: EmailAddress): JsObject = emailWrites.writes(email)
    override def reads(json: JsValue): JsResult[EmailAddress] = emailReads.reads(json)
  }

  implicit val passwordFormat: OFormat[Password] = new OFormat[Password] {
    val passwordReads: Reads[Password] = (__ \ "password").read[String](minLength[String](1)).map(Password.apply)
    val passwordWrites: OWrites[Password] = Json.writes[Password]
    override def writes(pw: Password): JsObject = passwordWrites.writes(pw)
    override def reads(json: JsValue): JsResult[Password] = passwordReads.reads(json)
  }

  implicit val signInFormat: OFormat[SignInInfo] = new OFormat[SignInInfo] {
    val signInInfoWrites: OWrites[SignInInfo] = Json.writes[SignInInfo]
    val signInInfoReads: Reads[SignInInfo] = (
      (__ \ "email").read[String](email) and
      (__ \ "password").read[String](minLength[String](1)) and
      (__ \ "rememberMe").read[Boolean]
    )(SignInInfo.apply _)
    override def writes(signInInfo: SignInInfo): JsObject = signInInfoWrites.writes(signInInfo)
    override def reads(json: JsValue): JsResult[SignInInfo] = signInInfoReads.reads(json)
  }

  val signUpFormat: OFormat[SignUpData] = new OFormat[SignUpData] {
    val signUpDataWrites: OWrites[SignUpData] = Json.writes[SignUpData]
    val signUpDataReads: Reads[SignUpData] = (
      (__ \ "name").read[String](minLength[String](3)) and
      (__ \ "email").read[String](email) and
        (__ \ "password").read[String](minLength[String](6))
      )(SignUpData.apply _)
    override def writes(signUpData: SignUpData): JsObject = signUpDataWrites.writes(signUpData)
    override def reads(json: JsValue): JsResult[SignUpData] = signUpDataReads.reads(json)
  }

  val oauth2InfoReads: Reads[OAuth2Info] =
    (
      ((__ \ "access_token").read[String] orElse (__ \ "accessToken").read[String]) and
      (__ \ "token_type").readNullable[String] and
      (__ \ "expires_in").readNullable[Int] and
      (__ \ "refreshToken").readNullable[String] and
      (__ \ "params").readNullable[Map[String, String]]
    )(OAuth2Info.apply _)

  /**
    * Implicit JSON formats.
    */
  trait Formats {

    /**
      * Renames a branch if it exists.
      */
    val RenameBranch: (JsPath, JsPath) => Reads[JsObject] = (oldPath: JsPath, newPath: JsPath) => {
      (__.json.update(newPath.json.copyFrom(oldPath.json.pick)) andThen oldPath.json.prune).orElse(__.json.pick[JsObject])
    }

    /**
      * Renames the field "_id" into the value given as `name` parameter.
      */
    val IDReads: String => Reads[JsObject] = (name: String) => RenameBranch(__ \ '_id, __ \ Symbol(name))

    /**
      * Transforms the field with the given `name` into the "_id" field.
      */
    val IDWrites: String => JsValue => JsObject = (name: String) => (js: JsValue) => {
      js.as[JsObject] - name ++ (js \ name match {
        case JsDefined(v) => Json.obj("_id" -> v)
        case _            => Json.obj()
      })
    }

    /**
      * Converts [[play.api.i18n.Lang]] object to JSON and vice versa.
      */
    implicit object LangFormat extends Format[Lang] {
      def reads(json: JsValue): JsResult[Lang] = JsSuccess(Lang(json.as[String]))
      def writes(o: Lang): JsValue = JsString(o.code)
    }

    /**
      * Converts UUID object to JSON and vice versa.
      */
    implicit object UUIDFormat extends Format[UUID] {
      def reads(json: JsValue): JsResult[UUID] = Try(UUID.fromString(json.as[String])) match {
        case Success(id) => JsSuccess(id)
        case Failure(e)  => JsError(e.getMessage)
      }
      def writes(o: UUID): JsValue = JsString(o.toString)
    }

    /**
      * Converts a [[Settings]] instance to JSON and vice versa.
      */
    implicit val settingsFormat: OFormat[Settings] = Json.format

    /**
      * Converts a [[Config]] instance to JSON and vice versa.
      */
    implicit val configFormat: OFormat[Config] = Json.format
  }

  trait CoreMongoFormats extends Formats {

    /**
      * Converts Instant object to JSON and vice versa.
      */
    implicit object InstantFormat extends Format[Instant] {
      def reads(json: JsValue): JsResult[Instant] =
        (__ \ "$date").read[Long].map(Instant.ofEpochMilli).reads(json)
      def writes(o: Instant): JsValue = Json.obj(
        "$date" -> o.toEpochMilli
      )
    }
  }

  /**
    * Mongo centric JSON formats.
    */
  object MongoFormats extends CoreMongoFormats with Formats {

    implicit val passwordInfoFormat: OFormat[PasswordInfo] = Json.format

    /**
      * Converts JSON into a [[models.auth.AuthToken]] instance.
      */
    implicit val authTokenReads: Reads[AuthToken] = IDReads("id") andThen Json.reads

    /**
      * Converts a [[models.auth.AuthToken]] instance to JSON.
      */
    implicit val authTokenWrites: OWrites[AuthToken] = Json.writes.transform(IDWrites("id"))
  }

}