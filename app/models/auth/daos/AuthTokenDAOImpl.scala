package models.auth.daos

import java.time.Instant
import java.util.UUID

import javax.inject.Inject
import models.auth.AuthToken
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.JsObjectDocumentWriter
import reactivemongo.play.json.BSONDocumentWrites
import utils.mongo.MongoModel

import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Give access to the [[models.auth.AuthToken]] object.
  *
  * @param reactiveMongoApi The ReactiveMongo API.
  * @param ec               The execution context.
  */
class AuthTokenDAOImpl @Inject() (reactiveMongoApi: ReactiveMongoApi)(
  implicit
  val ec: ExecutionContext
) extends AuthTokenDAO with MongoModel {

  import models.JsonFormats.MongoFormats.{authTokenReads, authTokenWrites}

  /**
    * The MongoDB collection.
    */
  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("auth.tokens"))

  /**
    * Finds a token by its ID.
    *
    * @param id The unique token ID.
    * @return The found token or None if no token for the given ID could be found.
    */
  def findByUUID(id: UUID): Future[Option[AuthToken]] = collection.flatMap(_.find[BSONDocument, AuthToken](BSONDocument("_id" -> id.toString), None).one[AuthToken])

  /**
    * Finds expired tokens.
    *
    * @param instant The current instant.
    */
  def findExpired(instant: Instant): Future[Seq[AuthToken]] = {
    val query = BSONDocument("expiry" -> BSONDocument("$lte" -> BSONDateTime(instant.toEpochMilli)))
    val res = collection
      .flatMap(_.find[BSONDocument, AuthToken](query, None)
      .cursor(ReadPreference.nearest)
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[AuthToken]]()))
    res
  }

  /**
    * Saves a token.
    *
    * If the token doesn't exists then it will be added, otherwise it will be updated.
    *
    * @param token The token to save.
    * @return The saved token.
    */
  def save(token: AuthToken): Future[AuthToken] = onSuccess(
    collection.flatMap(
      _.update(ordered = false)
        .one(Json.obj("_id" -> token.id), token, upsert = true)
    ), token)

  /**
    * Removes the token for the given ID.
    *
    * @param id The ID for which the token should be removed.
    * @return A future to wait for the process to be completed.
    */
  def remove(id: UUID): Future[Unit] = onSuccess(collection.flatMap(_.delete.one(Json.obj("_id" -> id))), ())
}
