package utils.auth

import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import controllers.common.ApiController
import javax.inject.Inject
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{ControllerComponents, RequestHeader, Result}

import scala.concurrent.Future

/**
 * Custom unsecured error handler.
 */
class CustomUnsecuredErrorHandler @Inject() (
  val controllerComponents: ControllerComponents
) extends UnsecuredErrorHandler with I18nSupport with ApiController {

  /**
   * Called when a user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful(Forbidden(MeterResponse("auth.forbidden", Messages("auth.forbidden"))))
  }
}
