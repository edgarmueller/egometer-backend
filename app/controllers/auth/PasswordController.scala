package controllers.auth

import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{PasswordHasherRegistry, PasswordInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import controllers.models.common.ApiController
import javax.inject.Inject
import models.auth.{AuthToken, EmailAddress, Password}
import models.auth.services.{AuthTokenService, UserService}
import net.ceedubs.ficus.Ficus._
import play.api.Configuration
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc._
import utils.JSRouter
import utils.auth.DefaultEnv

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The `Password` controller.
  *
  * @param controllerComponents   The Play controller components.
  * @param silhouette             The Silhouette stack.
  * @param userService            The user service implementation.
  * @param authInfoRepository     The auth info repository.
  * @param authTokenService       The auth token service implementation.
  * @param passwordHasherRegistry The password hasher registry.
  * @param mailerClient           The mailer client.
  * @param configuration          The Play configuration.
  * @param jsRouter               The JS router helper.
  * @param ex                     The execution context.
  */
class PasswordController @Inject() (
  val controllerComponents: ControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  authTokenService: AuthTokenService,
  passwordHasherRegistry: PasswordHasherRegistry,
  mailerClient: MailerClient,
  configuration: Configuration,
  jsRouter: JSRouter
)(
  implicit
  ex: ExecutionContext
) extends ApiController {


  import models.JsonFormats._

  /**
    * Requests an email with password recovery instructions.
    *
    * It sends an email to the given address if it exists in the database. Otherwise we do not show the user
    * a notice for not existing email addresses to prevent the leak of existing email addresses.
    *
    * @return A Play result.
    */
  def recover: Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    import models.JsonFormats._
    request.body.asJson
      .flatMap(_.validate[EmailAddress].asOpt)
      .map(emailAddress => {
        val loginInfo = LoginInfo(CredentialsProvider.ID, emailAddress.email)
        val result = Ok(MeterResponse("auth.password.recover.successful", Messages("auth.reset.email.sent")))
        userService.retrieve(loginInfo).flatMap {
          case Some(user) =>
            sendMail(user.id, emailAddress.email).map(_ => result)
          case None => Future.successful(BadRequest(MeterResponse("auth.user.not.found", Messages("auth.user.not.found"))))
        }
      })
      .getOrElse(
        Future.successful(BadRequest(
          MeterResponse("auth.password.recover.email.invalid", Messages("auth.password.recover.email.invalid"))
        ))
      )
  }

  /**
    * Resets the password.
    *
    * @param token The token to identify a user.
    * @return A Play result.
    */
  def reset(token: UUID): Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    validateToken(token, authToken =>
      request.body.asJson
        .flatMap(_.validate[Password].asOpt)
        .map(password =>
          userService.retrieve(authToken.userID).flatMap {
            case Some(user) if user.loginInfo.exists(_.providerID == CredentialsProvider.ID) =>
              val passwordInfo = passwordHasherRegistry.current.hash(password.password)
              val loginInfo = user.loginInfo.find(_.providerID == CredentialsProvider.ID).get
              authInfoRepository.update[PasswordInfo](loginInfo, passwordInfo).map { _ =>
                Ok(MeterResponse("auth.password.reset.successful", Messages("auth.password.reset")))
              }
            case _ => Future.successful(
              BadRequest(MeterResponse("auth.password.reset.token.invalid", Messages("auth.invalid.reset.link")))
            )
          }
        )
        .getOrElse(
          Future.successful(BadRequest(MeterResponse("auth.password.reset.invalid", Messages("auth.password.reset.invalid"))))
        )
    )
  }

  /**
    * An action that validates if a token is valid.
    *
    * @param token The token to validate.
    * @return A Play result.
    */
  def validate(token: UUID): Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    validateToken(token, _ => {
      Future.successful(Ok(MeterResponse("auth.password.reset.valid", Messages("auth.valid.reset.link"))))
    })
  }

  /**
    * A helper function which validates the reset token and either returns a HTTP 400 result in case of
    * invalidity or a block that returns another result in case of validity.
    *
    * @param token            The token to validate.
    * @param f                The block to execute if the token is valid.
    * @param messagesProvider The Play messages provider.
    * @return A Play result.
    */
  private def validateToken(token: UUID, f: AuthToken => Future[Result])(
    implicit
    messagesProvider: MessagesProvider
  ): Future[Result] = {
    authTokenService.validate(token).flatMap {
      case Some(authToken) => f(authToken)
      case None =>
        Future.successful(
          BadRequest(MeterResponse("auth.password.reset.invalid", Messages("auth.invalid.reset.link")))
        )
    }
  }

  private def sendMail(userId: UUID, email: String)
    (implicit request: Request[AnyContent], messagesProvider: MessagesProvider) = {

    val c = configuration.underlying
    val tokenExpiry = c.getAs[FiniteDuration](s"auth.authToken.expiry").getOrElse(5.minutes)
    authTokenService.create(userId, tokenExpiry).map { authToken =>
      val url = jsRouter.absoluteURL(s"/auth/recover/password/${authToken.id}")
      mailerClient.send(Email(
        subject = Messages("auth.email.reset.password.subject"),
        from = Messages("email.from"),
        to = Seq(email),
        bodyText = Some(s"TODO: resetPassword $url"), //auth.views.txt.emails.resetPassword(user, url).body),
        bodyHtml = Some(s"TODO resetPassword $url") //auth.views.html.emails.resetPassword(user, url).body)
      ))
    }
  }
}
