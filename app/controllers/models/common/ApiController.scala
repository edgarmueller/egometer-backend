package controllers.models.common

import play.api.http.Writeable
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsValue, Json, OWrites, Writes}
import play.api.mvc.BaseController

/**
 * The base API controller.
 */
trait ApiController extends BaseController with I18nSupport {

  /**
   * Straightforward `Writeable` for ApiResponse[T] values.
   */
  implicit def apiResponseWritable[T](
    implicit
    jsonWrites: Writes[T],
    jsonWritable: Writeable[JsValue]
  ): Writeable[MeterResponse[T]] = {
    jsonWritable.map(response => Json.toJson(response))
  }

  /**
   * An API response.
   *
   * @param code        The response code.
   * @param description The response description.
   * @param details     A list with details.
   * @tparam T The type of the detail.
   */
  case class MeterResponse[T](code: String, description: String, details: T)
  object MeterResponse {
    def apply(code: String, description: String): MeterResponse[List[String]] = {
      MeterResponse(code, description, List())
    }
    implicit def jsonWrites[T](implicit detail: Writes[T]): Writes[MeterResponse[T]] = {
      OWrites[MeterResponse[T]] { response =>
        Json.obj(
          "code" -> response.code,
          "description" -> response.description,
          "details" -> detail.writes(response.details)
        )
      }
    }
  }
}
