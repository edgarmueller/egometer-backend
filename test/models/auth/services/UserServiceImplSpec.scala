package models.auth.services

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID._
import java.util.{Locale, UUID}

import com.mohiva.play.silhouette.api.LoginInfo
import models.{Settings, User}
import models.auth.Registration
import models.auth.daos.UserDao
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import play.api.i18n.Lang
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class UserServiceImplSpec extends PlaySpecification with Mockito with NoLanguageFeatures {

  "The `save` method" should {
    "create an user and save it in the DB" in new Context {
      dao.save(any[User]) answers { p => Future.successful(p.asInstanceOf[User]) }

      await(service.save(user))

      there was one(dao).save(any[User])
    }

    "find an user by id" in  new Context {
      dao.find(any[UUID]) answers { p => Future.successful(Some(user)) }
      await(service.findById(user.id))
      there was one(dao).find(user.id)
    }

    "find an user by login info" in  new Context {
      dao.find(any[LoginInfo]) answers { p => Future.successful(Some(user)) }
      await(service.retrieve(user.loginInfo.head))
      there was one(dao).find(user.loginInfo.head)
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The current clock.
     */
    val clock: Clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

    /**
     * The user DAO.
     */
    val dao: UserDao = mock[UserDao].smart

    val registration = Registration(
      Lang(Locale.US),
      "1.2.3.4",
      None,
      None,
      activated = true,
      Instant.now()
    )

    val settings = Settings(
      Lang(Locale.US),
      None
    )

    val user = User(
      randomUUID(),
      Seq(LoginInfo(
        "credentials",
        "foobar@test.de"
      )),
      None,
      None,
      None,
      registration,
      settings
    )

    /**
     * The auth token service implementation.
     */
    val service = new UserServiceImpl(dao, clock)
  }
}
