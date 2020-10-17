package controllers.auth

import java.time.Clock
import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.AvatarService
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.providers._
import controllers.common.ApiController
import io.swagger.annotations.Api
import javax.inject.Inject
import models.auth.{Registration, SignUpData}
import models.auth.services.{AuthTokenService, UserService}
import models.{JsonFormats, Settings, User}
import net.ceedubs.ficus.Ficus._
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.libs.json.JsError
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc._
import utils.JSRouter
import utils.auth.DefaultEnv

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * The `Sign Up` controller.
 *
 * @param controllerComponents   The Play controller components.
 * @param silhouette             The Silhouette stack.
 * @param userService            The user service implementation.
 * @param authInfoRepository     The auth info repository implementation.
 * @param authTokenService       The auth token service implementation.
 * @param avatarService          The avatar service implementation.
 * @param passwordHasherRegistry The password hasher registry.
 * @param mailerClient           The mailer client.
 * @param configuration          The Play configuration.
 * @param clock                  The clock instance.
 * @param executionContext                     The execution context.
 */
@Api(value = "/sign-up")
class SignUpController @Inject() (
  val controllerComponents: ControllerComponents,
  silhouette:               Silhouette[DefaultEnv],
  userService:              UserService,
  authInfoRepository:       AuthInfoRepository,
  authTokenService:         AuthTokenService,
  avatarService:            AvatarService,
  passwordHasherRegistry:   PasswordHasherRegistry,
  mailerClient:             MailerClient,
  configuration:            Configuration,
  clock:                    Clock,
  jsRouter:                 JSRouter
)(
  implicit
  executionContext: ExecutionContext
) extends ApiController {

  /**
   * Sign up a user.
   *
   * @return A Play result.
   */
  def signUp: Action[AnyContent] = (silhouette.UnsecuredAction andThen httpErrorRateLimitFunction).async {
    implicit request =>

      if (configuration.underlying.getOrElse("egometer.features.signUp", true)) {
        readSignUpData(request) match {
          case Left(error) =>
            Future.successful(BadRequest(
              MeterResponse("auth.signUp.invalid.data", Messages("auth.signUp.invalid.data"), JsError.toJson(error))
            ))
          case Right(signUpData) =>
            val loginInfo = LoginInfo(CredentialsProvider.ID, signUpData.email)
            userService.retrieve(loginInfo).flatMap {
              case Some(user) if !user.registration.activated => handleInactiveUser(signUpData, user)
              case Some(user) => handleExistingUser(signUpData, user)
              case None => signUpNewUser(signUpData, loginInfo)
            }.map { _ =>
              Created(MeterResponse("auth.signUp.successful", Messages("auth.sign.up.email.sent", signUpData.email)))
            }
        }
      } else {
        Future(BadRequest(MeterResponse("auth.signUp.disabled", Messages("auth.signUp.disabled"))))
      }
  }

  /**
   * Sign up an existing user.
   *
   * @param data    The form data.
   * @param user    The user data.
   * @param request The request header.
   * @return A future to wait for the computation to complete.
   */
  private def handleExistingUser(data: SignUpData, user: User)(
    implicit
    request: RequestHeader
  ): Future[Unit] = Future {
    val url = jsRouter.absoluteURL("/auth/sign-in")
    mailerClient.send(Email(
      subject = Messages("auth.email.already.signed.up.subject"),
      from = Messages("email.from"),
      to = Seq(data.email),
      bodyText = Some(s"You already signed up. login instead $url"), //Some(auth.views.txt.emails.alreadySignedUp(user, url).body),
      bodyHtml = Some(s"You already signed up. login instead $url") //Some(auth.views.html.emails.alreadySignedUp(user, url).body)
    ))
  }

  /**
   * Sign up a new user.
   *
   * @param data      The form data.
   * @param loginInfo The login info.
   * @param request   The request header.
   * @return A future to wait for the computation to complete.
   */
  private def signUpNewUser(data: SignUpData, loginInfo: LoginInfo)(
    implicit
    request: RequestHeader
  ): Future[Unit] = {
    val c = configuration.underlying
    val tokenExpiry = c.getAs[FiniteDuration](s"auth.authToken.expiry").getOrElse(5 minutes)
    val authInfo = passwordHasherRegistry.current.hash(data.password)
    val user = User(
      id = UUID.randomUUID(),
      loginInfo = Seq(loginInfo),
      name = Some(data.name),
      email = Some(data.email),
      avatarURL = None,
      registration = Registration(
        lang = request.lang,
        ip = request.remoteAddress,
        host = request.headers.get(HeaderNames.HOST),
        userAgent = request.headers.get(HeaderNames.USER_AGENT),
        activated = false,
        dateTime = clock.instant()
      ),
      settings = Settings(
        lang = request.lang,
        timeZone = None
      )
    )
    for {
      avatar <- avatarService.retrieveURL(data.email)
      user <- userService.save(user.copy(avatarURL = avatar))
      _ <- authInfoRepository.add(loginInfo, authInfo)
      authToken <- authTokenService.create(user.id, tokenExpiry)
    } yield {
      val url = jsRouter.absoluteURL("/auth/account/activation/" + authToken.id)
      mailerClient.send(Email(
        subject = Messages("auth.email.sign.up.subject"),
        from = Messages("email.from"),
        to = Seq(data.email),
        bodyText = Some(s"Please follow the link to complete your registration $url"), //auth.views.txt.emails.signUp(user, url).body),
        bodyHtml = Some(s"Please follow the link to complete your registration $url") //auth.views.html.emails.signUp(user, url).body)
      ))

      silhouette.env.eventBus.publish(SignUpEvent(user, request))
    }
  }

  private def readSignUpData(request: Request[AnyContent]): Either[JsError, SignUpData] = {
    request.body.asJson match {
      case None => Left(JsError("Invalid JSON"))
      case Some(userJson) =>
        JsonFormats.signUpFormat.reads(userJson).asEither.left.map(JsError(_))
    }
  }

  /**
    * Sign up a new user.
    *
    * @param data      The form data.
    * @param request   The request header.
    * @return A future to wait for the computation to complete.
    */
  private def handleInactiveUser(data: SignUpData, user: User)(
    implicit
    request: RequestHeader
  ): Future[Unit] = {
    val c = configuration.underlying
    val tokenExpiry = c.getAs[FiniteDuration](s"auth.authToken.expiry").getOrElse(5 minutes)
    for {
        authToken <- authTokenService.create(user.id, tokenExpiry)
    } yield {
      val url = jsRouter.absoluteURL("/auth/account/activation/" + authToken.id)
      mailerClient.send(Email(
        subject = Messages("auth.email.sign.up.subject"),
        from = Messages("email.from"),
        to = Seq(data.email),
        bodyText = Some(s"Please follow the link to complete your registration $url"), //auth.views.txt.emails.signUp(user, url).body),
        bodyHtml = Some(s"Please follow the link to complete your registration $url") //auth.views.html.emails.signUp(user, url).body)
      ))

      silhouette.env.eventBus.publish(SignUpEvent(user, request))
    }
  }
}
