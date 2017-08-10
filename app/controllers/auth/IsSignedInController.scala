package controllers.auth

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import org.joda.time.Period
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.JsonResults
import utils.auth.DefaultEnv

import scala.concurrent.ExecutionContext

class IsSignedInController @Inject()(
                                      silhouette: Silhouette[DefaultEnv],
                                      components: ControllerComponents)
                                    (implicit ex: ExecutionContext)
  extends AbstractController(components) with I18nSupport {


  def isSignedIn: Action[AnyContent] = silhouette.SecuredAction.async { request =>
    silhouette.env.authenticatorService.retrieve(request).map(_.fold(
      // TODO: I think this case can never happen
      BadRequest(JsonResults.error("Invalid token"))
    )(t =>
      // TODO: can we provide a sensible return value?
      Ok(Json.obj(
        "ttl" -> new Period(t.lastUsedDateTime, t.expirationDateTime).getSeconds
      ))
    ))
  }

}
