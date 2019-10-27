package models.schema

import java.util.UUID

import play.api.libs.json.JsObject

case class SchemaDto(id: Option[String], name: String, userId: Option[UUID], schema: JsObject)

object SchemaDto {
  def toDto(schema: Schema): SchemaDto = {
    SchemaDto(
      schema._id.map(_.stringify),
      schema.name,
      schema.userId,
      schema.schema
    )
  }
}