package models.auth.services

import java.time.Clock
import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import javax.inject.Inject
import models.auth.Registration
import models.{Settings, User}
import models.auth.daos.UserDao
import play.api.http.HeaderNames
import play.api.i18n
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

/**
 * Handles actions to users.
 *
 * @param userDAO The user DAO implementation.
 * @param ex      The execution context.
 */
class UserServiceImpl @Inject()(userDAO: UserDao, clock: Clock)(implicit ex: ExecutionContext) extends UserService {

  /**
   * Monkey patch the `CommonSocialProfile` class.
   *
   * @param profile The class to patch.
   */
  implicit class RichCommonSocialProfile(profile: CommonSocialProfile) {
    def name: Option[String] = profile.fullName.orElse {
      profile.firstName -> profile.lastName match {
        case (Some(f), Some(l)) => Some(f + " " + l)
        case (Some(f), None) => Some(f)
        case (None, Some(l)) => Some(l)
        case _ => None
      }
    }
  }

  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param id The ID to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given ID.
   */
  def retrieve(id: UUID): Future[Option[User]] = userDAO.find(id)

  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userDAO.find(loginInfo)

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User): Future[User] = userDAO.save(user)

  /**
   * Saves the social profile for a user.
   *
   * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
   *
   * @param profile The social profile to save.
   * @return The user for whom the profile was saved.
   */
  def save(profile: CommonSocialProfile)(implicit request: RequestHeader, lang: i18n.Lang): Future[User] = {
    userDAO.find(profile.loginInfo).flatMap {
      // Update user with profile
      case Some(user) =>
        userDAO.save(user.copy(
          name = profile.name,
          email = profile.email.orElse(user.email), // Do not override existing email with empty email
          avatarURL = profile.avatarURL
        ))
      // Insert a new user
      case None =>
        userDAO.save(User(
          id = UUID.randomUUID(),
          loginInfo = Seq(profile.loginInfo),
          name = profile.name,
          email = profile.email,
          avatarURL = profile.avatarURL,
          registration = Registration(
            lang = lang,
            ip = request.remoteAddress,
            host = request.headers.get(HeaderNames.HOST),
            userAgent = request.headers.get(HeaderNames.USER_AGENT),
            activated = true,
            dateTime = clock.instant()
          ),
          settings = Settings(
            lang = lang,
            timeZone = None
          )
        ))
    }
  }
}
