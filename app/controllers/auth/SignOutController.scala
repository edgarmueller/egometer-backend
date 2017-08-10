package controllers.auth

import com.mohiva.play.silhouette.api.{LogoutEvent, Silhouette}
import controllers.models.common.ApiController
import javax.inject.Inject
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.auth.DefaultEnv

/**
  * The `Sign Out` controller.
  *
  * @param controllerComponents  The Play controller components.
  * @param silhouette            The Silhouette stack.
  */
class SignOutController @Inject() (
  val controllerComponents: ControllerComponents,
  silhouette: Silhouette[DefaultEnv]
) extends ApiController {

  /**
    * Sign out a user.
    *
    * @return A Play result.
    */
  def signOut: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, Ok(MeterResponse(
      "auth.signOut.successful",
      Messages("auth.signed.out")
    )))
  }
}
