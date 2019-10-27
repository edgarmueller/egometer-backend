package controllers.models

import com.eclipsesource.schema.JsonSource
import com.eclipsesource.schema.drafts.Version7
import com.mohiva.play.silhouette.api.Silhouette
import controllers.common.WithValidator
import io.swagger.annotations._
import javax.inject.Inject
import models.JsonFormats._
import models.ErrorResponse
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import reactivemongo.bson.BSONObjectID
import models.schema.{SchemaDto, SchemasDao, Schema => MeterSchema}
import utils.auth.{DefaultEnv}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Controller for managing schemas.
  */
@Api(value = "/schemas")
class SchemasController @Inject()(
                                   controllerComponents: ControllerComponents,
                                   schemasDao: SchemasDao,
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
    schemasDao
      .query(
        Json.obj(
          "$or" -> Json.arr(
            Json.obj("userId" -> user.id),
            Json.obj("userId" -> JsNull)
          )
        )
      )
      .map { meterSchemas => Ok(Json.toJson(meterSchemas.map(SchemaDto.toDto))) }
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
    schemasDao
      .queryFirst(
        Json.obj(
          "_id" -> Json.obj("$oid" -> meterSchemaId),
          "userId" -> user.id
        )
      )
      .map { maybeMeter =>
        maybeMeter
          .map { meter => Ok(Json.toJson(SchemaDto.toDto(meter))) }
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
    // TODO: remove flatMap once validator has been fixed
    val user = req.identity
    validate(req.body)
      .flatMap(meterSchemaFormat.reads)
      .map(
        meterSchema => {
          val meterSchemaWithId = meterSchema.copy(
            _id = Some(BSONObjectID.generate()),
            userId = Some(user.id)
          )
          schemasDao.addMeterSchema(meterSchemaWithId)
            .map { res =>
              if (res.ok) Created(Json.toJson(SchemaDto.toDto(meterSchemaWithId)))
              else InternalServerError(Json.toJson(ErrorResponse("create.schema.failed")))
            }
        }
      )
      .fold(
        errs => Future.successful(BadRequest(ErrorResponse("invalid.meter.format", errs).toJson)),
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
    schemasDao
        .deleteSchema(
          Json.obj(
            "_id" -> Json.obj("$oid" -> meterSchemaId),
            "userId" -> user.id
          )
        )
      .map {
        case Some(schema@MeterSchema(_, _, _, _)) => Ok(Json.toJson(SchemaDto.toDto(schema)))
        case None => NotFound
      }
  }
}