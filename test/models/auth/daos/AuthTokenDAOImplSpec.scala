package models.auth.daos

import java.time.Instant
import java.util.UUID

import spec.MongoSpecification
import models.auth.AuthToken
import play.api.test.WithApplication
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.ExecutionContext.Implicits.global

class AuthTokenDAOImplSpec extends MongoSpecification {

  "The `find` method" should {
    "find a token for the given ID" in new Context {
      new WithApplication(application) with MongoScope {
        await(dao.findByUUID(id)) must beSome(token)
      }
    }

    "return None if no auth info for the given login info exists" in new Context {
      new WithApplication(application) with MongoScope {
        await(dao.findByUUID(UUID.randomUUID())) must beNone
      }
    }
  }

  "The `findExpired` method" should {
    "find expired tokens" in new Context {
      new WithApplication(application) with MongoScope {
        val result: Seq[AuthToken] = await(dao.findExpired(token.expiry.plusSeconds(5)))
        result must be equalTo Seq(token)
      }
    }
  }

  "The `save` method" should {
    "insert a new token" in new Context {
      new WithApplication(application) with MongoScope {
        val newToken: AuthToken = token.copy(id = UUID.randomUUID())

        await(dao.save(newToken)) must be equalTo newToken
        await(dao.findByUUID(newToken.id)) must beSome(newToken)
      }
    }

    "update an existing token" in new Context {
      new WithApplication(application) with MongoScope {
        val updatedToken: AuthToken = token.copy(expiry = Instant.now())

        await(dao.save(updatedToken)) must be equalTo updatedToken
        await(dao.findByUUID(token.id)) must beSome(updatedToken)
      }
    }
  }

  "The `remove` method" should {
    "remove a token" in new Context {
      new WithApplication(application) with MongoScope {
        await(dao.remove(id))
        await(dao.findByUUID(id)) must beNone
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends MongoContext {

    /**
     * The test fixtures to insert.
     */
    override val fixtures = Map(
      "auth.tokens" -> Seq("models/auth-tokens/token.json")
    )

    /**
     * The auth token DAO implementation.
     */
    val dao = new AuthTokenDAOImpl(injector.instanceOf[ReactiveMongoApi])

    /**
     * An ID for the stored token.
     * Matches id from token.json
     */
    val id: UUID = UUID.fromString("bdd4e520-8803-4f7d-ab67-9b50c12e9919")

    /**
     * The stored auth token.
     */
    val token = AuthToken(
      id = id,
      // matches user id from token.json
      userID = UUID.fromString("c51c255a-f5b4-4e5c-9afc-d5a9381b4287"),
      expiry = Instant.ofEpochMilli(1493826799375L)
    )
  }
}
