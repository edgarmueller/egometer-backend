package controllers.auth

import java.time.Clock

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers._
import controllers.common.ApiController
import javax.inject.Inject
import models.auth.{SignInInfo, SignInToken}
import models.auth.services.UserService
import models.User
import net.ceedubs.ficus.Ficus._
import org.joda.time.DateTime
import play.api.Configuration
import play.api.i18n.{I18nSupport, Messages, MessagesProvider}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import utils.auth.DefaultEnv

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The `Sign In` controller.
  *
  * @param components             The Play controller components.
  * @param silhouette             The Silhouette stack.
  * @param userService            The user service implementation.
  * @param credentialsProvider    The credentials provider.
  * @param configuration          The Play configuration.
  * @param clock                  The clock instance.
  */
class SignInController @Inject() (
                                   components:             ControllerComponents,
                                   silhouette:             Silhouette[DefaultEnv],
                                   userService:            UserService,
                                   credentialsProvider:    CredentialsProvider,
                                   configuration:          Configuration,
                                   clock:                  Clock
                                 )(implicit ex: ExecutionContext, actorSystem: ActorSystem)
  extends AbstractController(components) with ApiController with I18nSupport {

  import models.JsonFormats._

  def signIn: Action[AnyContent] = (silhouette.UnsecuredAction andThen httpErrorRateLimitFunction).async { implicit request =>
    import models.JsonFormats._
    request.body.asJson
      .flatMap(_.validate[SignInInfo].asOpt)
      .fold(
        Future.successful(BadRequest(MeterResponse("auth.invalid.credentials", Messages("auth.invalid.credentials"))))
      )(signInInfo => {
        val credentials = Credentials(signInInfo.email, signInInfo.password)
        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
          userService.retrieve(loginInfo).flatMap {
            case Some(user) if !user.registration.activated =>
              handleInactiveUser(user)
            case Some(user) =>
              handleActiveUser(user, loginInfo, signInInfo.rememberMe)
            case None =>
              Future.failed(new IdentityNotFoundException("invalid.user"))
          }
        }.recover {
          case _: ProviderException =>
            Unauthorized(MeterResponse("auth.invalid.credentials", Messages("auth.invalid.credentials")))
        }
      })
  }

  /**
    * Handles the inactive user.
    *
    * @param user The inactive user.
    * @param messagesProvider The Play messages provider.
    * @return A Play result.
    */
  private def handleInactiveUser(user: User)(implicit messagesProvider: MessagesProvider): Future[Result] = {
    Future.successful(
      Locked(MeterResponse(
        "auth.signIn.account.inactive",
        Messages("auth.account.inactive"),
        Json.obj("email" -> user.email)
      ))
    )
  }

  /**
    * Handles the active user.
    *
    * @param user       The active user.
    * @param loginInfo  The login info for the current authentication.
    * @param rememberMe True if the cookie should be a persistent cookie, false otherwise.
    * @param request    The current request header.
    * @return A Play result.
    */
  private def handleActiveUser(
                                user:       User,
                                loginInfo:  LoginInfo,
                                rememberMe: Boolean
                              )(implicit request: RequestHeader): Future[Result] = {
    silhouette.env.authenticatorService.create(loginInfo)
      .map(configureAuthenticator(rememberMe, _))
      .flatMap { authenticator =>
        silhouette.env.eventBus.publish(LoginEvent(user, request))
        silhouette.env.authenticatorService
          .init(authenticator.copy(customClaims = makeCustomClaims(user)))
          .map { token =>
            Ok(
              MeterResponse(
                "auth.signIn.successful",
                Messages("auth.signed.in"),
                Json.toJson(SignInToken(
                  user,
                  token,
                  authenticator.expirationDateTime
                ))
              )
            )
          }
      }
  }

  private def makeCustomClaims(user: User): Option[JsObject] = {
    Some(Json.obj("role" -> user.role.name))
  }

  /**
    * Changes the default authenticator config if the remember me flag was activated during sign-in.
    *
    * @param rememberMe    True if the cookie should be a persistent cookie, false otherwise.
    * @param authenticator The authenticator instance.
    * @return The changed authenticator if the remember me flag was activated, otherwise the unchanged authenticator.
    */
  private def configureAuthenticator(rememberMe: Boolean, authenticator: DefaultEnv#A): DefaultEnv#A = {
    if (rememberMe) {
      val c = configuration.underlying
      val configPath = "silhouette.authenticator.rememberMe"
      val authenticatorExpiry = c.as[FiniteDuration](s"$configPath.authenticatorExpiry").toMillis
      val instant = clock.instant().plusMillis(authenticatorExpiry)
      val expirationDateTime = new DateTime(instant.toEpochMilli)

      authenticator.copy(
        expirationDateTime = expirationDateTime,
        idleTimeout = c.getAs[FiniteDuration]("silhouette.authenticator.rememberMe.authenticatorIdleTimeout")
      )
    } else {
      authenticator
    }
  }
}
