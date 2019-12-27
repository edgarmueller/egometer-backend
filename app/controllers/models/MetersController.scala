package controllers.models

import com.eclipsesource.schema.JsonSource
import com.mohiva.play.silhouette.api.Silhouette
import controllers.common.WithValidator
import io.swagger.annotations._
import javax.inject.Inject
import models._
import models.entry.MeterEntriesService
import models.meter.{MeterDto, MetersService}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import utils.auth.DefaultEnv

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "/meters")
class MetersController @Inject()(
                                  controllerComponents: ControllerComponents,
                                  metersService: MetersService,
                                  meterEntriesService: MeterEntriesService,
                                  silhouette: Silhouette[DefaultEnv]
                                ) extends AbstractController(controllerComponents) with WithValidator {

  import JsonFormats._
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
    metersService
      .findByUserId(user.id.toString)
      .map(meters => Ok(Json.toJson(meters)))
  }

  @ApiOperation(
    value = "Get a Meter",
    response = classOf[MeterDto]
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "meter.not.found")
  ))
  def getMeter(@ApiParam(value = "The id of the Meter to fetch") meterId: String): Action[AnyContent] =
    silhouette.SecuredAction.async { req =>
      val user = req.identity
      ifMeterExists(meterId, user.id.toString, meter => {
        Future.successful(Ok(Json.toJson(meter)))
      })
    }

  @ApiOperation(
    value = "Add a new Meter",
    response = classOf[Void],
    code = 201
  )
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid Meter format")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      value = "The Meter to add, in Json Format",
      required = true,
      dataType = "models.meter.MeterDto",
      paramType = "body"
    )
  ))
  def createMeter: Action[JsValue] = silhouette.SecuredAction.async(parse.json) {
    req => {
      val user = req.identity
      validate(req.body)
        .flatMap(meterFormat.reads)
        .map { meter =>
          metersService
            .addMeter(meter.copy(userId = Some(user.id)))
            .map(meter => Created(Json.toJson(meter)))
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
      ifMeterExists(meterId, user.id.toString, _ => {
        meterEntriesService
          .deleteByMeterId(meterId)
          .flatMap { _ =>
            metersService
              .deleteById(meterId)
              .map {
                case Some(meter) => Ok(Json.toJson(MeterDto.toDto(meter)))
                case None => NotFound(Json.toJson(ErrorResponse("meter.not.found")))
              }
          }
      })
    }

  @ApiOperation(
    value = "Update a Meter",
    response = classOf[MeterDto]
  )
  @ApiResponses(Array(new ApiResponse(code = 400, message = "Invalid Meter format")))
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
        validate(req.body)
          .flatMap(meterFormat.reads)
          .map { meter =>
            ifMeterExists(meterId, user.id.toString, _ => {
              metersService
                .updateById(meterId, meter)
                .map(_.fold(NotFound("meter.not.found"))(updatedMeter => Created(Json.toJson(updatedMeter))))
            })
          }
          .fold(errors => Future.successful(
            BadRequest(Json.toJson(ErrorResponse("invalid.meter", errors)))), x => x
          )
    }

  private def ifMeterExists(meterId: String, userId: String, body: MeterDto => Future[Result]) = {
    metersService
      .findById(meterId)
      .flatMap(_.fold(Future.successful(NotFound("meter.not.found")))(meter =>
        if (meter.userId.exists(_.toString == userId)) {
          body(meter)
        }else {
          Future.successful(Forbidden("not.allowed"))
        }
      ))
  }
}
