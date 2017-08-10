package models

import java.util.UUID

import com.google.inject.Inject
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

case class SchemaDao(id: Option[String], name: String, userId: Option[UUID], schema: JsObject)

object SchemaDao {
  def toDao(schema: Schema): SchemaDao = {
    SchemaDao(
      schema._id.map(_.stringify),
      schema.name,
      schema.userId,
      schema.schema
    )
  }
}

case class Schema(_id: Option[BSONObjectID], name: String, userId: Option[UUID], schema: JsObject)

class SchemaRepo @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi){

  import JsonFormats.meterSchemaFormat

  def schemaCollection: Future[JSONCollection] =
    reactiveMongoApi.database.map(_.collection("schemas"))

  def query(selector: JsObject): Future[Seq[Schema]] = {
    schemaCollection.flatMap(_.find(selector)
      .cursor[Schema](ReadPreference.primary)
      .collect[Seq](100, Cursor.FailOnError[Seq[Schema]]())
    )
  }

  def queryFirst(selector: JsObject): Future[Option[Schema]] =
    schemaCollection.flatMap(_.find(selector).one[Schema])

  def findById(schemaId: String): Future[Option[Schema]] = {
    BSONObjectID.parse(schemaId)
      .fold(
        Future.failed,
        id => schemaCollection.flatMap(_.find(BSONDocument("_id" -> id)).one[Schema])
      )
  }

  def getMeterSchema(meterSchemaId: BSONObjectID): Future[Option[Schema]] = {
    val query = BSONDocument("_id" -> meterSchemaId)
    schemaCollection.flatMap(_.find(query).one[Schema])
  }

  def addMeterSchema(meterSchema: Schema): Future[WriteResult] = {
    schemaCollection.flatMap(_.insert(meterSchema))
  }

  def deleteSchema(query: JsObject): Future[Option[Schema]] = {
    schemaCollection.flatMap(_.findAndRemove(query).map(_.result[Schema]))
  }
}
