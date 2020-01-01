package models.schema

import com.google.inject.Inject
import controllers.common.WithValidator
import play.api.libs.json.{JsNull, Json}

import scala.concurrent.{ExecutionContext, Future}

class SchemasService @Inject()(schemaDao: SchemasDao)(implicit ec: ExecutionContext)
  extends WithValidator {
  def addSchema(schema: Schema): Future[Option[SchemaDto]] = {
    schemaDao
      .addMeterSchema(schema)
      .map(res => if (res.ok) Some(SchemaDto.toDto(schema)) else None)
  }

  def findByUserId(userId: String): Future[Seq[SchemaDto]] = {
    schemaDao
      .query(
        Json.obj(
          "$or" -> Json.arr(
            Json.obj("userId" -> userId),
            Json.obj("userId" -> JsNull)
          )
        )
      ).map(_.map(SchemaDto.toDto))
  }

  def findById(schemaId: String): Future[Option[SchemaDto]] =
    schemaDao.findById(schemaId)
      .map(_.map(SchemaDto.toDto))

  def deleteById(schemaId: String): Future[Option[SchemaDto]] =
    schemaDao
      .deleteSchema(Json.obj("_id" -> Json.obj("$oid" -> schemaId)))
      .map(_.map(SchemaDto.toDto))
}
