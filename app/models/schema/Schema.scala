package models.schema

import java.util.UUID

import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

case class Schema(_id: Option[BSONObjectID], name: String, userId: Option[UUID], schema: JsObject)
