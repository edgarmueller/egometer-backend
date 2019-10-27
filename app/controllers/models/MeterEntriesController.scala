package controllers.models

import com.eclipsesource.schema._
import com.eclipsesource.schema.drafts.Version7
import com.github.nscala_time.time.Imports._
import com.mohiva.play.silhouette.api.Silhouette
import controllers.common.{ApiController, WithValidator}
import io.swagger.annotations._
import javax.inject.Inject
import models.JsonFormats._
import models._
import models.entry.{MeterEntry, MeterEntryDto, MeterEntriesDao}
import models.meter.{Meter, MetersDao}
import models.schema.{Schema, SchemasDao}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc._
import utils.auth.DefaultEnv

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Controller for managing entries of a meter.
  */
@Api(value = "/entries")
class MeterEntriesController @Inject()(
                                        controllerComponents: ControllerComponents,
                                        schemaDao: SchemasDao,
                                        metersDao: MetersDao,
                                        meterEntriesDao: MeterEntriesDao,
                                        silhouette: Silhouette[DefaultEnv]
                                    )(implicit ec: ExecutionContext)
  extends AbstractController(controllerComponents) with ApiController with WithValidator {

  import Version7._

  @ApiOperation(
    value = "Create a meter entry",
    response = classOf[MeterEntryDto],
    code = 201
  )
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "invalid.meter.entry.format"),
    new ApiResponse(code = 404, message = "meter.not.found"),
    new ApiResponse(code = 500, message = "schema.not.found")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      value = "The meter entry to be added, in JSON format",
      required = true,
      dataType = "models.meter.MeterEntryDto",
      paramType = "body"
    )
  ))
  def upsertMeterEntry(meterId: String, date: String): Action[JsValue] = silhouette.SecuredAction.async(parse.json) { req =>
    meterEntryUpdateFormat.reads(req.body)
      .map(updateEntry =>
        metersDao
          .findById(meterId)
          .flatMap(
            _.fold(
              Future(NotFound(ErrorResponse("meter.not.found").toJson))
            )(upsertEntry(MeterEntry(None, meterId, updateEntry.value, JsString(date))))
          )
      )
      .getOrElse(
        Future.successful(BadRequest(ErrorResponse("invalid.meter.entry.format").toJson))
      )
  }

  @ApiOperation(
    value = "Get all available meter entries per meter for a month based on the given date",
    response = classOf[MeterEntryDto],
    responseContainer = "Map"
  )
  def findEntriesByDate(date: String, days: Option[Int]): Action[AnyContent] = {
    import JsonFormats._
    import org.joda.time.format.DateTimeFormat
    silhouette.SecuredAction.async { implicit request =>
      val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
      Try { fmt.parseLocalDate(date) }
        .map(fromDate => {
          val startDate = fromDate.minusDays(fromDate.getDayOfMonth - 1)
          val endDate = fromDate.plusDays(days.getOrElse(31))

          val selector = Json.obj(
            "date" -> Json.obj(
              "$gte" -> startDate.toString,
              "$lte" -> endDate.toString
            )
          )

          findEntries(selector).map(groupedEntries =>
            Ok(Json.toJson(groupedEntries))
          )
        })
        .getOrElse(
          Future.successful(
            BadRequest(MeterResponse("invalid.date.format", Messages("invalid.date.format")))
          )
        )
    }
  }

  @ApiOperation(
    value = "Get all available meter entries per meter for a month based on the given date",
    response = classOf[MeterEntryDto],
    responseContainer = "Map"
  )
  def findMeterEntriesByDate(date: String, meterId: String, days: Option[Int]): Action[AnyContent] = {
    import JsonFormats._
    import org.joda.time.format.DateTimeFormat
    silhouette.SecuredAction.async { _ =>
      val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
      val fromDate: LocalDate = fmt.parseLocalDate(date)

      val startDate = fromDate.minusDays(fromDate.getDayOfMonth - 1)
      val endDate = fromDate.plusDays(days.getOrElse(31))

      val selector = Json.obj(
        "date" -> Json.obj(
          "$gte" -> startDate.toString,
          "$lte" -> endDate.toString
        ),
        "meterId" -> meterId
      )

      findEntries(selector).map(groupedEntries => Ok(Json.toJson(groupedEntries)))
    }
  }

  @ApiOperation(
    value = "Delete a meter entry",
    response = classOf[Boolean]
  )
  def deleteEntry(entryId: String): Action[AnyContent] = {
    silhouette.SecuredAction.async { _ =>
      meterEntriesDao.deleteEntries(Json.obj("_id" -> Json.obj("$oid" -> entryId)))
        .map { writeResult => {
          if (writeResult.ok) {
            Ok(Json.toJson(writeResult.ok))
          } else {
            BadRequest(Json.toJson("entry.does.not.exist"))
          }
        }}
    }
  }

  private def findEntries(selector: JsObject): Future[Map[String, Seq[MeterEntryDto]]] = {
    meterEntriesDao
      .query(selector)
      .map {
        entries => {
          val grouped: Map[String, Seq[MeterEntry]] = entries.seq.groupBy(_.meterId)
          grouped.mapValues(entries => entries.map(MeterEntryDto.toDto))
        }
      }
  }

  private def upsertEntry(entry: MeterEntry)(meter: Meter): Future[Result] = {
    schemaDao
      .findById(meter.schemaId)
      .flatMap(maybeSchema =>
        maybeSchema
          .flatMap(parseSchema)
          .map(upsertValidEntry(entry))
          .getOrElse(Future(InternalServerError(ErrorResponse(s"schema.not.found").toJson)))
      )
  }

  private def parseSchema(meterSchema: Schema): Option[SchemaType] = {
    Json.fromJson[SchemaType](meterSchema.schema).asOpt
  }

  private def upsertValidEntry(entry: MeterEntry)(schema: SchemaType): Future[Result] = {
    validator.validate(schema)(entry.value)
      .fold(
        errors => Future(BadRequest(ErrorResponse(errors).toJson)),
        _ => meterEntriesDao.upsertEntry(entry)
          .map { upsertedEntry =>
            upsertedEntry
              .map(e =>
                Ok(Json.toJson(MeterEntryDto.toDto(e))))
              .getOrElse(NotFound)
          }
      )
  }
}
