package controllers.auth

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import com.mohiva.play.silhouette.impl.providers._
import javax.inject.Inject
import models.JsonFormats
import models.auth.services.UserService
import play.api.i18n.{I18nSupport, Lang}
import play.api.libs.json._
import play.api.mvc._
import utils.auth.DefaultEnv

import scala.concurrent.{ExecutionContext, Future}

/**
 * The social auth controller.
 *
 * @param components             The Play controller components.
 * @param silhouette             The Silhouette stack.
 * @param userService            The user service implementation.
 * @param authInfoRepository     The auth info service implementation.
 * @param socialProviderRegistry The social provider registry.
 * @param ex                     The execution context.
 */
class SocialAuthController @Inject()(
  components: ControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  socialProviderRegistry: SocialProviderRegistry
)(
  implicit
  ex: ExecutionContext
) extends AbstractController(components) with I18nSupport with Logger {

  def authenticateToken(provider: String): Action[JsValue] = Action.async(parse.json) { implicit request => {
    implicit val lang: Lang = request.messages.lang

    val token  = request.body.validate[OAuth2Info](JsonFormats.oauth2InfoReads).asOpt
    ((socialProviderRegistry.get[SocialProvider](provider), token) match {
      case (Some(p: OAuth2Provider with CommonSocialProfileBuilder), Some(authInfo)) =>
        for {
          profile <- p.retrieveProfile(authInfo)
          user <- userService.save(profile)
          _ <- authInfoRepository.save(profile.loginInfo, authInfo)
          authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
          value <- silhouette.env.authenticatorService.init(authenticator)
        } yield {
          silhouette.env.eventBus.publish(LoginEvent(user, request))
          Ok(Json.obj("token" -> value))
        }

      case (_, None) =>
        Future.failed(
          new OAuth2StateException(s"No token found in the request while authenticating with $provider")
        )
      case _ =>
        Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: OAuth2StateException =>
        logger.error("Unexpected token error", e)
        NotFound
      case e: ProviderException =>
        logger.error("Unexpected provider error", e)
        NotFound
    }
  }
  }
}
