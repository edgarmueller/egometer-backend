package models

import play.api.libs.json._

object MeterError {

  def apply(errors: Seq[(JsPath, Seq[JsonValidationError])]): MeterError =
    MeterError(errors.head._2.head.messages, JsError(errors.tail))

  def apply(msg: String, errors: Seq[(JsPath, Seq[JsonValidationError])]): MeterError =
    MeterError(Seq(msg), JsError(errors))

  def apply(msg: String): MeterError = {
    emptyError.withMessage(msg)
  }

  implicit class MeterExtensionEx(meterError: MeterError) {

    import JsonFormats.meterErrorWrites

    def toJson: JsValue = {
      Json.toJson(meterError)
    }
  }

  private val emptyError = MeterError(msgs = Seq.empty, JsError())
}

case class MeterError(msgs: Seq[String], jsError: JsError) {

  // FIXME: do we need deep merge?
  def merge(otherError: MeterError): MeterError = {
    MeterError(
      msgs ++ otherError.msgs,
      JsError.merge(jsError, otherError.jsError)
    )
  }

  def withMessage(msg: String): MeterError = {
    this.copy(msgs =  msg +: msgs)
  }
}
