package controllers.models

import com.eclipsesource.schema.JsonSource
import com.eclipsesource.schema.drafts.Version7
import com.mohiva.play.silhouette.api.Silhouette
import controllers.common.WithValidator
import io.swagger.annotations._
import javax.inject.Inject
import models.ErrorResponse
import models.JsonFormats._
import models.schema.{SchemaDto, SchemasService}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import utils.auth.DefaultEnv

import scala.concurrent.{ExecutionContext, Future}

/**
  * Controller for managing schemas.
  */
@Api(value = "/schemas")
class SchemasController @Inject()(
                                   controllerComponents: ControllerComponents,
                                   schemasService: SchemasService,
                                   silhouette: Silhouette[DefaultEnv]
                                )(implicit ec: ExecutionContext)
  extends AbstractController(controllerComponents) with WithValidator {

  import Version7._

  // :)
  private val MeterMetaSchema =
    """{
       |"type": "object",
       |"properties": {
       |  "name": {
       |    "type": "string",
       |    "minLength": 1
       |  },
       |  "schema": {
       |    "$ref": "classpath:///json-schema-draft-07.json"
       |  }
       |},
       |"required": ["name", "schema"]
       |}
    """.stripMargin

  // TODO: all validate functions of the validator should be curried such that we can apply the schema partially
  // TODO: we also need to be able to pass in a type parameter that represents the read type
  private val meterMetaSchema = JsonSource.schemaFromString(MeterMetaSchema).get
  private val validate: ValidateFn = validator.validate(meterMetaSchema)

  @ApiOperation(
    value = "Get all available meter schemas",
    response = classOf[SchemaDto],
    responseContainer = "List"
  )
  def getAllMeterSchemas: Action[AnyContent] = silhouette.SecuredAction.async { req =>
    val user = req.identity
    schemasService
      .findByUserId(user.id.toString)
      .map { schemas => Ok(Json.toJson(schemas)) }
  }

  @ApiOperation(
    value = "Get a meter schema by its id",
    response = classOf[SchemaDto]
  )
  @ApiResponses(Array(
      new ApiResponse(code = 404, message = "schema.not.found", response = classOf[ErrorResponse])
    )
  )
  def getMeterSchema(
                      @ApiParam(value = "The id of the meter schema to fetch") meterSchemaId: String
                    ): Action[AnyContent] = silhouette.SecuredAction.async{ req =>
    val user = req.identity
    schemasService.findById(meterSchemaId)
      .map { maybeSchema =>
        maybeSchema
          .map { schemaDto => Ok(Json.toJson(schemaDto)) }
          .getOrElse(NotFound(Json.toJson(ErrorResponse("schema.not.found"))))
      }
  }

  @ApiOperation(
    value = "Add a new meter schema",
    code = 201
  )
  @ApiResponses(
    Array(
      new ApiResponse(
        code = 400,
        message = "invalid.meter.format",
        response = classOf[ErrorResponse]
      ),
      new ApiResponse(
        code = 500,
        message = "create.schema.failed",
        response = classOf[ErrorResponse]
      )
    )
  )
  @ApiImplicitParams(Array(
      new ApiImplicitParam(
        value = "The schema to be added, in JSON format",
        required = true,
        dataType = "models.schema.SchemaDto",
        paramType = "body"
      )
    )
  )
  def addMeterSchema(): Action[JsValue] = silhouette.SecuredAction.async(parse.json){ req =>
    val user = req.identity
    validate(req.body)
      .flatMap(meterSchemaFormat.reads)
      .map(
        meterSchema => {
          val meterSchemaWithId = meterSchema.copy(
            _id = Some(BSONObjectID.generate()),
            userId = Some(user.id)
          )
          schemasService.addSchema(meterSchemaWithId)
            .map {
              _.fold(InternalServerError(Json.toJson(ErrorResponse("create.schema.failed"))))(schemaDto =>
                Created(Json.toJson(schemaDto))
              )
            }
        }
      )
      .fold(
        errors => Future.successful(BadRequest(ErrorResponse("invalid.meter.format", errors).toJson)),
        identity
      )
  }

  @ApiOperation(
    value = "Delete a schema",
    response = classOf[SchemaDto]
  )
  def deleteMeterSchema(
                         @ApiParam(value = "The id of the schema to delete") meterSchemaId: String
                       ): Action[AnyContent] = silhouette.SecuredAction.async { req =>
    val user = req.identity
    ifSchemaExists(meterSchemaId, user.id.toString, _ =>
      schemasService.deleteById(meterSchemaId)
        .map {
          case Some(schemaDto) => Ok(Json.toJson(schemaDto))
          case None => NotFound
        }
    )
  }

  private def ifSchemaExists(schemaId: String, userId: String, body: SchemaDto => Future[Result]) = {
    schemasService
      .findById(schemaId)
      .flatMap(_.fold(Future.successful(NotFound("schema.not.found")))(schema =>
        if (schema.userId.exists(_.toString == userId) || schema.userId.isEmpty) {
          body(schema)
        } else {
          Future.successful(Forbidden("not.allowed"))
        }
      ))
  }
}
