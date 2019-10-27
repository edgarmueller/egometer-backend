package controllers.auth

import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import controllers.common.ApiController
import javax.inject.Inject
import models.auth.EmailAddress
import models.auth.services.{AuthTokenService, UserService}
import net.ceedubs.ficus.Ficus._
import play.api.Configuration
import play.api.data.validation._
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.json.Json
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import utils.JSRouter
import utils.auth.DefaultEnv
import utils.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The `Account` controller.
  *
  * @param controllerComponents  The Play controller components.
  * @param silhouette            The Silhouette stack.
  * @param userService           The user service implementation.
  * @param authTokenService      The auth token service implementation.
  * @param mailerClient          The mailer client.
  * @param configuration         The Play configuration.
  * @param jsRouter              The JS router helper.
  * @param ex                    The execution context.
  */
class AccountController @Inject() (
  val controllerComponents: ControllerComponents,
  silhouette:               Silhouette[DefaultEnv],
  userService:              UserService,
  authTokenService:         AuthTokenService,
  mailerClient:             MailerClient,
  configuration:            Configuration,
  jsRouter:                 JSRouter
)(
  implicit
  ex: ExecutionContext
) extends ApiController with Logger {

  /**
    * Sends an account activation email to the user with the given email.
    *
    * @return A Play result.
    */
  def send: Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    import models.JsonFormats._
    request.body.asJson.map(json => {
        Json.fromJson[EmailAddress](json).asOpt.fold(
          Future.successful(BadRequest(
            MeterResponse("auth.account.activate.email.invalid", Messages("auth.account.activate.email.invalid"))
          ))
        )(emailAddress => {
          Constraints.emailAddress.apply(emailAddress.email) match {
            case Invalid(_) =>
              Future.successful(
                BadRequest(MeterResponse("auth.account.activate.email.invalid", Messages("auth.account.activate.email.invalid")))
              )
            case Valid =>
              val result = Ok(MeterResponse("auth.account.send.successful", Messages("auth.activation.email.sent", emailAddress.email)))
              val loginInfo = LoginInfo(CredentialsProvider.ID, emailAddress.email)
              userService.retrieve(loginInfo).flatMap {
                case Some(user) if !user.registration.activated =>
                  sendMail(user.id, emailAddress.email).map(_ => result)
                case _ => Future.successful(result)
              }
          }
        })
      }
    ).getOrElse(
      Future.successful(UnsupportedMediaType(MeterResponse("invalid.json", Messages("invalid.json"))))
    )
  }

  private def sendMail(userId: UUID, email: String)
    (implicit request: Request[AnyContent], messagesProvider: MessagesProvider): Future[String] = {

    val c = configuration.underlying
    val tokenExpiry = c.getAs[FiniteDuration](s"auth.authToken.expiry").getOrElse(5.minutes)
    authTokenService.create(userId, tokenExpiry).map { authToken =>
      val url = jsRouter.absoluteURL("/auth/account/activation/" + authToken.id)
      mailerClient.send(Email(
        subject = Messages("auth.email.activate.account.subject"),
        from = Messages("email.from"),
        to = Seq(email),
        bodyText = Some(s"TODO: $url"), // Some(auth.views.txt.emails.activateAccount(user, url).body),
        bodyHtml = Some(s"TODO: $url") //Some(auth.views.html.emails.activateAccount(user, url).body)
      ))
    }
  }

  /**
    * Activates an account.
    *
    * @param token The token to identify a user.
    * @return A Play result.
    */
  def activate(token: UUID): Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    logger.info("Activating token: " + token)
    authTokenService.validate(token).flatMap {
      case Some(authToken) => userService.retrieve(authToken.userID).flatMap {
        case Some(user) if user.loginInfo.exists(_.providerID == CredentialsProvider.ID) =>
          userService.save(user.copy(registration = user.registration.copy(activated = true))).map { _ =>
            Ok(MeterResponse("auth.account.activate.successful", Messages("auth.account.activated")))
          }
        case _ =>
          Future.successful(
            BadRequest(MeterResponse("auth.account.activate.invalid", Messages("auth.invalid.activation.link")))
          )
      }
      case None =>
        Future.successful(
          BadRequest(MeterResponse("auth.account.activate.invalid.token", Messages("auth.invalid.activation.link")))
        )
    }
  }
}
