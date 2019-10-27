package models.auth.daos

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.persistence.exceptions.MongoException
import javax.inject.Inject
import models.User
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.JsObjectDocumentWriter
import reactivemongo.play.json.BSONDocumentWrites

import scala.concurrent.{ExecutionContext, Future}

/**
  * Give access to the user object.
  */
class UserDaoImpl @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi) extends UserDao {

  import models.JsonFormats.userFormat

  def userCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("users"))

  /**
    * Finds a user by its login info.
    *
    * @param loginInfo The login info of the user to find.
    * @return The found user or None if no user for the given login info could be found.
    */
  def find(loginInfo: LoginInfo): Future[Option[User]] = {
    userCollection
      .flatMap(
        _.find[BSONDocument, User](
          BSONDocument("loginInfo" ->
            BSONDocument(
              "providerID" -> loginInfo.providerID,
              "providerKey" -> loginInfo.providerKey
            )
          )
        ).one[User]
      )
  }

  /**
    * Finds a user by its user ID.
    *
    * @param userId The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  def find(userId: UUID): Future[Option[User]] =
    userCollection.flatMap(_.find[BSONDocument, User](BSONDocument("id" -> userId.toString)).one[User])

  /**
    * Returns some result on success and None on error.
    *
    * @param result The last result.
    * @param entity The entity to return.
    * @tparam T The type of the entity.
    * @return The entity on success or an exception on error.
    */
  protected def onSuccess[T](result: Future[WriteResult], entity: T): Future[T] = result.recoverWith {
    case e => Future.failed(new MongoException("Got exception from MongoDB", e.getCause))
  }.map { r =>
    WriteResult.lastError(r) match {
      case Some(e) => throw new MongoException(e.message, e)
      case _       => entity
    }
  }


  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User): Future[User] = {
    onSuccess(userCollection.flatMap(
      _.update(
        ordered = false)
        .one(
          // TODO: id
          Json.obj("id" -> user.id.toString),
          user,
          upsert = true
        )
    ), user)
  }
}