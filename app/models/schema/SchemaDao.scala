package models.schema

import java.util.UUID

import play.api.libs.json.JsObject

case class SchemaDao(id: Option[String], name: String, userId: Option[UUID], schema: JsObject)

object SchemaDao {
  def toDao(schema: Schema): SchemaDao = {
    SchemaDao(
      schema._id.map(_.stringify),
      schema.name,
      schema.userId,
      schema.schema
    )
  }
}