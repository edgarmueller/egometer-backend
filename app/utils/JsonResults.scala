package utils

import play.api.libs.json.{JsArray, JsNull, JsObject, Json}

object JsonResults {

  private val StatusKey = "status"
  // TODO: needed?
  //  private val StatusCodeKey = "statusCode"
  private val DataKey = "data"
  private val MessagesKey = "messages"
  // TODO: needed?
  // private val ErrorsKey = "errors"

  def error(message: String): JsObject = error(Seq(message))

  def error(messages: Seq[String]): JsObject = Json.obj(
    StatusKey -> "error",
    DataKey -> JsNull,
    MessagesKey -> JsArray(messages.map(Json.toJson(_)))
  )
}