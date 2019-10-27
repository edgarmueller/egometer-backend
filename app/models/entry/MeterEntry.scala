package models.entry

import play.api.libs.json.{JsString, JsValue}
import reactivemongo.bson.BSONObjectID

case class MeterEntry(
                       _id: Option[BSONObjectID],
                       meterId: String,
                       value: JsValue,
                       date: JsString
                     )
