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
import models.entry.{MeterEntriesByMeterDto, MeterEntriesDao, MeterEntriesService, MeterEntry, MeterEntryDto}
import models.meter.{Meter, MetersDao}
import models.schema.{Schema, SchemasDao}
import org.joda.time.IllegalFieldValueException
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
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
                                        meterEntriesService: MeterEntriesService,
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
      dataType = "models.entry.MeterEntryDto",
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
              Future(BadRequest(ErrorResponse("meter.not.found").toJson))
            )(meter =>
              meterEntriesService.upsertEntry(MeterEntry(None, meterId, updateEntry.value, JsString(date)))(meter)
              .map(result => result.fold(
                errors => BadRequest(errors.toJson),
                maybeResult => maybeResult.fold(BadRequest("schema.not.found"))(entry => Ok(Json.toJson(entry))
              )))
            )
          )
      )
      .getOrElse(
        Future.successful(BadRequest(ErrorResponse("invalid.meter.entry.format").toJson))
      )
  }

  @ApiOperation(
    value = "Get all entries for a given time given month or week of a year",
    response = classOf[MeterEntriesByMeterDto],
  )
  def findEntries(
                   year: Option[Int],
                   month: Option[Int],
                   week: Option[Int]
                 ): Action[AnyContent] = {
    silhouette.SecuredAction.async { implicit request =>
      val y = year.getOrElse(new DateTime().getYear)
      val m = month.getOrElse(new DateTime().getMonthOfYear)
      week
        .map(w => meterEntriesService.findEntriesByYearAndWeek(y, w))
        .getOrElse(meterEntriesService.findEntriesByYearAndMonth(y, m))
        .map(envelopeDto => Ok(Json.toJson(envelopeDto)))
        .recover {
          case _: IllegalFieldValueException => BadRequest("invalid week")
        }
    }
  }

  @ApiOperation(
    value = "Delete a meter entry",
    response = classOf[MeterEntryDto]
  )
  def deleteEntry(entryId: String): Action[AnyContent] = {
    silhouette.SecuredAction.async { _ =>
      if (BSONObjectID.parse(entryId).isFailure) {
        Future.successful(BadRequest("invalid id format"))
      } else {
        val entryToBeDeleted = meterEntriesService.findById(entryId)
        entryToBeDeleted.flatMap(maybeEntry =>
          maybeEntry.fold(Future.successful(NotFound("not found")))(entry =>
            meterEntriesService
              .deleteEntryById(entryId)
              .map(deleted => {
                if (deleted) Ok(Json.toJson(entry))
                else InternalServerError("could not delete entry")
              })
          )
        )
      }
    }
  }
}
