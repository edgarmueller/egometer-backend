package models.schema

import com.google.inject.Inject
import models.JsonFormats
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

class SchemasRepository @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi){

  import JsonFormats.meterSchemaFormat

  def schemaCollection: Future[JSONCollection] =
    reactiveMongoApi.database.map(_.collection("schemas"))

  def query(selector: JsObject): Future[Seq[Schema]] = {
    schemaCollection.flatMap(_.find[JsObject, Schema](selector)
      .cursor[Schema](ReadPreference.primary)
      .collect[Seq](100, Cursor.FailOnError[Seq[Schema]]())
    )
  }

  def queryFirst(selector: JsObject): Future[Option[Schema]] =
    schemaCollection.flatMap(_.find[JsObject, Schema](selector).one[Schema])

  def findById(schemaId: String): Future[Option[Schema]] = {
    BSONObjectID.parse(schemaId)
      .fold(
        Future.failed,
        id => schemaCollection.flatMap(_.find[BSONDocument, Schema](BSONDocument("_id" -> id)).one[Schema])
      )
  }

  def getMeterSchema(meterSchemaId: BSONObjectID): Future[Option[Schema]] = {
    val query = BSONDocument("_id" -> meterSchemaId)
    schemaCollection.flatMap(_.find[BSONDocument, Schema](query).one[Schema])
  }

  def addMeterSchema(meterSchema: Schema): Future[WriteResult] = {
    schemaCollection.flatMap(_.insert(ordered = false).one(meterSchema))
  }

  def deleteSchema(query: JsObject): Future[Option[Schema]] = {
    schemaCollection.flatMap(_.findAndRemove(query).map(_.result[Schema]))
  }
}
