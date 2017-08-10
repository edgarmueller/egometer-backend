package controllers.models

import com.eclipsesource.schema._
import com.eclipsesource.schema.drafts.Version7
import com.github.nscala_time.time.Imports._
import com.mohiva.play.silhouette.api.Silhouette
import controllers.models.common.{ApiController, WithValidator}
import io.swagger.annotations._
import javax.inject.Inject
import models.JsonFormats._
import models._
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc._
import utils.auth.DefaultEnv

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Controller for managing entries of a meter.
  */
@Api(value = "/entries")
class MeterEntryController @Inject()(
                                      cc: ControllerComponents,
                                      schemaRepo: SchemaRepo,
                                      meterRepo: MeterRepo,
                                      meterEntryRepo: MeterEntryRepo,
                                      silhouette: Silhouette[DefaultEnv]
                                    )(implicit ec: ExecutionContext)
  extends AbstractController(cc) with ApiController with WithValidator {

  import Version7._

  @ApiOperation(
    value = "Create a meter entry",
    response = classOf[MeterEntry],
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
      dataType = "models.MeterEntry",
      paramType = "body"
    )
  ))
  def upsertMeterEntry(meterId: String, date: String): Action[JsValue] = silhouette.SecuredAction.async(parse.json) { req =>
    meterEntryUpdateFormat.reads(req.body)
      .map(updateEntry =>
        meterRepo
          .findById(meterId)
          .flatMap(
            _.fold(
              Future(NotFound(MeterError("meter.not.found").toJson))
            )(upsertEntry(MeterEntry(None, meterId, updateEntry.value, JsString(date))))
          )
      )
      .getOrElse(
        Future.successful(BadRequest(MeterError("invalid.meter.entry.format").toJson))
      )
  }

  @ApiOperation(
    value = "Get all available meter entries per meter for a month based on the given date",
    response = classOf[MeterEntry],
    responseContainer = "Map"
  )
  def findMonthlyEntriesByDate(date: String): Action[AnyContent] = {
    import JsonFormats._
    import org.joda.time.format.DateTimeFormat
    silhouette.SecuredAction.async { implicit request =>
      val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
      Try { fmt.parseLocalDate(date) }
        .map(fromDate => {
          val startDate = fromDate.minusDays(fromDate.getDayOfMonth - 1)
          val maxDayOfMonth: Int = fromDate.dayOfMonth().withMaximumValue().getDayOfMonth
          val endDate = fromDate.plusDays(maxDayOfMonth - fromDate.getDayOfMonth)

          val selector = Json.obj(
            "date" -> Json.obj(
              "$gte" -> startDate.toString,
              "$lte" -> endDate.toString
            )
          )

          findEntries(selector).map(groupedEntries => Ok(Json.toJson(groupedEntries)))
        })
        .getOrElse(Future.successful(BadRequest(MeterResponse("invalid.date.format", Messages("invalid.date.format")))))
    }
  }

  @ApiOperation(
    value = "Get all available meter entries per meter for a month based on the given date",
    response = classOf[MeterEntry],
    responseContainer = "Map"
  )
  def findMonthlyMeterEntriesByDate(date: String, meterId: String): Action[AnyContent] = {
    import JsonFormats._
    import org.joda.time.format.DateTimeFormat
    silhouette.SecuredAction.async { _ =>
      val fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
      val fromDate: LocalDate = fmt.parseLocalDate(date)

      val startDate = fromDate.minusDays(fromDate.getDayOfMonth - 1)
      val maxDayOfMonth: Int = fromDate.dayOfMonth().withMaximumValue().getDayOfMonth
      val endDate = fromDate.plusDays(maxDayOfMonth - fromDate.getDayOfMonth)

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

  private def findEntries(selector: JsObject): Future[Map[String, Seq[MeterEntryDao]]] = {
    meterEntryRepo
      .query(selector)
      .map {
        entries => {
          val grouped: Map[String, Seq[MeterEntry]] = entries.seq.groupBy(_.meterId)
          grouped.mapValues(entries => entries.map(MeterEntryDao.toDao))
        }
      }
  }

  private def upsertEntry(entry: MeterEntry)(meter: Meter): Future[Result] = {
    schemaRepo
      .findById(meter.schemaId)
      .flatMap(maybeSchema =>
        maybeSchema
          .flatMap(parseSchema)
          .map(upsertValidEntry(entry))
          .getOrElse(Future(InternalServerError(MeterError(s"schema.not.found").toJson)))
      )
  }

  private def parseSchema(meterSchema: Schema): Option[SchemaType] = {
    Json.fromJson[SchemaType](meterSchema.schema).asOpt
  }

  private def upsertValidEntry(entry: MeterEntry)(schema: SchemaType): Future[Result] = {
    validator.validate(schema)(entry.value)
      .fold(
        errors => Future(BadRequest(MeterError(errors).toJson)),
        _ => meterEntryRepo.upsertEntry(entry)
          .map { upsertedEntry =>
            upsertedEntry
              .map(e =>
                Ok(Json.toJson(MeterEntryDao.toDao(e))))
              .getOrElse(NotFound)
          }
      )
  }
}
