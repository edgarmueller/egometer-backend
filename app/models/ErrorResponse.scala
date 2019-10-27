package models

import play.api.libs.json._

object ErrorResponse {

  def apply(errors: Seq[(JsPath, Seq[JsonValidationError])]): ErrorResponse =
    ErrorResponse(errors.head._2.head.messages, JsError(errors.tail))

  def apply(msg: String, errors: Seq[(JsPath, Seq[JsonValidationError])]): ErrorResponse =
    ErrorResponse(Seq(msg), JsError(errors))

  def apply(msg: String): ErrorResponse = {
    emptyError.withMessage(msg)
  }

  implicit class MeterExtensionEx(meterError: ErrorResponse) {

    import JsonFormats.meterErrorWrites

    def toJson: JsValue = {
      Json.toJson(meterError)
    }
  }

  private val emptyError = ErrorResponse(msgs = Seq.empty, JsError())
}

case class ErrorResponse(msgs: Seq[String], jsError: JsError) {

  // FIXME: do we need deep merge?
  def merge(otherError: ErrorResponse): ErrorResponse = {
    ErrorResponse(
      msgs ++ otherError.msgs,
      JsError.merge(jsError, otherError.jsError)
    )
  }

  def withMessage(msg: String): ErrorResponse = {
    this.copy(msgs =  msg +: msgs)
  }
}
