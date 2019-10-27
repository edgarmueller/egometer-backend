package models.auth.services

import java.time.Clock
import java.util.UUID

import javax.inject.Inject
import models.auth.AuthToken
import models.auth.daos.AuthTokenDao
import utils.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * Handles actions to auth tokens.
 *
 * @param authTokenDAO The auth token DAO implementation.
 * @param clock        The clock instance.
 * @param ex           The execution context.
 */
class AuthTokenServiceImpl @Inject() (
                                       authTokenDAO: AuthTokenDao,
                                       clock: Clock
)(
  implicit
  ex: ExecutionContext
) extends AuthTokenService with Logger {

  /**
   * Creates a new auth token and saves it in the backing store.
   *
   * @param userID The user ID for which the token should be created.
   * @param expiry The duration a token expires.
   * @return The saved auth token.
   */
  def create(userID: UUID, expiry: FiniteDuration = 5 minutes): Future[AuthToken] = {
    val expiryDate = clock.instant().plusSeconds(expiry.toSeconds)
    val token = AuthToken(UUID.randomUUID(), userID, expiryDate)
    logger.info(s"Saving token for user $userID")
    authTokenDAO.save(token)
  }

  /**
   * Validates a token ID.
   *
   * @param id The token ID to validate.
   * @return The token if it's valid, None otherwise.
   */
  def validate(id: UUID): Future[Option[AuthToken]] = authTokenDAO.findByUUID(id)

  /**
   * Cleans expired tokens.
   *
   * @return The list of deleted tokens.
   */
  def clean: Future[Seq[AuthToken]] = authTokenDAO.findExpired(clock.instant()).flatMap { tokens =>
    logger.info("Cleaning tokens...")
    Future.sequence(tokens.map { token =>
      authTokenDAO.remove(token.id).map(_ => token)
    })
  }
}
