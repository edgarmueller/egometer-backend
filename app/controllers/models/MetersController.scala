package controllers.models

import com.eclipsesource.schema.JsonSource
import com.mohiva.play.silhouette.api.Silhouette
import controllers.common.WithValidator
import io.swagger.annotations._
import javax.inject.Inject
import models.JsonFormats._
import models._
import models.entry.MeterEntriesDao
import models.meter.{Meter, MeterDto, MetersDao}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.auth.DefaultEnv

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "/meters")
class MetersController @Inject()(
                                  controllerComponents: ControllerComponents,
                                  metersDao: MetersDao,
                                  meterEntriesDao: MeterEntriesDao,
                                  silhouette: Silhouette[DefaultEnv]
                               ) extends AbstractController(controllerComponents) with WithValidator {

  import com.eclipsesource.schema.drafts.Version7._

  private val MeterSchema =
    """{
       |"type": "object",
       |"properties": {
       |  "name": {
       |    "type": "string"
       |  },
       |  "schemaId": {
       |    "type": "string"
       |  }
       |},
       |"required": ["name", "schemaId"]
       |}
    """.stripMargin

  private val meterSchema = JsonSource.schemaFromString(MeterSchema).get
  private val validate: ValidateFn = validator.validate(meterSchema)

  @ApiOperation(
    value = "Get all available meters",
    response = classOf[MeterDto],
    responseContainer = "List"
  )
  def getAllMeters: Action[AnyContent] = silhouette.SecuredAction.async { req =>
    val user = req.identity
    metersDao
      .query(Json.obj("userId" -> user.id))
      .map { meters => Ok(Json.toJson(meters.map(MeterDto.toDto))) }
  }

  @ApiOperation(
    value = "Get a Meter",
    response = classOf[MeterDto]
  )
  @ApiResponses(Array(
      new ApiResponse(code = 404, message = "meter.not.found")
    )
  )
  def getMeter(@ApiParam(value = "The id of the Meter to fetch") meterId: String): Action[AnyContent] =
    silhouette.SecuredAction.async { req =>
      val user = req.identity
      metersDao
        .queryFirst(
          Json.obj(
            "userId" -> user.id,
            "_id" -> Json.obj("$oid" -> meterId)
          )
        )
        .map { maybeMeter =>
          maybeMeter
            .filter(meter => meter.userId.contains(user.id))
            .map(meter => Ok(Json.toJson(MeterDto.toDto(meter))))
            .getOrElse(NotFound(Json.toJson(ErrorResponse("meter.not.found"))))
        }
    }

  @ApiOperation(
    value = "Add a new Meter",
    response = classOf[Void],
    code = 201
  )
  @ApiResponses(Array(
      new ApiResponse(code = 400, message = "Invalid Meter format")
    )
  )
  @ApiImplicitParams(Array(
      new ApiImplicitParam(
        value = "The Meter to add, in Json Format",
        required = true,
        dataType = "models.meter.MeterDto",
        paramType = "body"
      )
    )
  )
  def createMeter: Action[JsValue] = silhouette.SecuredAction.async(parse.json) {
    req => {
      val user = req.identity
      validate(req.body)
        .flatMap(meterFormat.reads)
        .map { meter =>
          val meterWithId = meter.copy(userId = Some(user.id))
          metersDao
            .addMeter(meterWithId)
            .map { _ => Created(Json.toJson(MeterDto.toDto(meterWithId))) }
        }
        .fold(errors => Future.successful(BadRequest(Json.toJson(ErrorResponse("invalid.meter", errors)))), identity)
    }
  }

  @ApiOperation(
    value = "Delete a Meter",
    response = classOf[MeterDto]
  )
  @ApiResponses(
    Array(
      new ApiResponse(
        code = 404,
        message = "meter.not.found",
        response = classOf[ErrorResponse]
      )
    )
  )
  def deleteMeter(@ApiParam(value = "The id of the Meter to delete") meterId: String): Action[AnyContent] =
    silhouette.SecuredAction.async { req =>
      val user = req.identity
      // delete all related entries first
      meterEntriesDao
        .deleteEntries(Json.obj("meterId" -> meterId))
        .flatMap { _ =>
          metersDao
            .deleteMeter(Json.obj(
              "userId" -> user.id,
              "_id" -> Json.obj("$oid" -> meterId)
            ))
            .map {
              case Some(meter) => Ok(Json.toJson(MeterDto.toDto(meter)))
              case None => NotFound(Json.toJson(ErrorResponse("meter.not.found")))
            }
        }
    }

  @ApiOperation(
    value = "Update a Meter",
    response = classOf[MeterDto]
  )
  @ApiResponses(
    Array(new ApiResponse(code = 400, message = "Invalid Meter format"))
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        value = "The updated Meter, in Json Format",
        required = true,
        dataType = "models.meter.MeterDto",
        paramType = "body"
      )
    )
  )
  def updateMeter(@ApiParam(value = "The id of the Meter to update") meterId: String): Action[JsValue] =
    silhouette.SecuredAction.async(parse.json) {
      req =>
        val user = req.identity
        val selector = Json.obj(
          "_id" -> Json.obj("$oid" -> meterId),
          "userId" -> user.id
        )
        validate(req.body)
          .flatMap(meterFormat.reads)
          .map { meter =>
            metersDao.updateMeter(selector, meter).map {
              _.map(m => Created(Json.toJson(MeterDto.toDto(m))))
                .getOrElse(NotFound)
            }
          }
          .fold(errors => Future.successful(
            BadRequest(Json.toJson(ErrorResponse("invalid.meter", errors)))), identity
          )
    }
}
