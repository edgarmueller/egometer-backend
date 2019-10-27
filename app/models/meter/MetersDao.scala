package models.meter

import javax.inject.Inject
import models.JsonFormats
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}







class MetersDao @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi){

  import JsonFormats.meterFormat

  def meterCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("meters"))

  def findById(meterId: String): Future[Option[Meter]] = {
    BSONObjectID.parse(meterId)
      .fold(
        Future.failed,
        id => meterCollection.flatMap(_.find[BSONDocument, Meter](BSONDocument("_id" -> id)).one[Meter])
      )
  }

  def query(selector: JsObject): Future[Seq[Meter]] = {
    meterCollection.flatMap(_.find[JsObject, Meter](selector)
      .cursor[Meter](ReadPreference.primary)
      .collect[Seq](100, Cursor.FailOnError[Seq[Meter]]())
    )
  }

  def queryFirst(selector: JsObject): Future[Option[Meter]] = {
    meterCollection.flatMap(_.find[JsObject, Meter](selector).one[Meter])
  }

  def exists(meterId: String): Future[Boolean] = {
    BSONObjectID.parse(meterId)
      .fold(
        Future.failed,
        id => meterCollection.flatMap(_.find[BSONDocument, Meter](BSONDocument("_id" -> id)).one[Meter].map(_.isDefined))
      )
  }

  def getMeter(id: BSONObjectID): Future[Option[Meter]] = {
    val query = BSONDocument("_id" -> id)
    meterCollection.flatMap(_.find[BSONDocument, Meter](query).one[Meter])
  }

  def addMeter(meter: Meter): Future[WriteResult] = {
    // TODO: validate meter
    // TODO: validate existence of schema with given schema id
    meterCollection.flatMap(_.insert(ordered = false).one(meter))
  }

  def deleteMeter(query: JsObject): Future[Option[Meter]] = {
    meterCollection.flatMap(_.findAndRemove(query).map(_.result[Meter]))
  }

  def updateMeter(selector: JsObject, meter: Meter): Future[Option[Meter]] = {
    val updateModifier = BSONDocument(
      "$set" -> BSONDocument(
        "color" -> meter.color,
        "widget" -> meter.widget,
        "name" -> meter.name,
        "icon" -> meter.icon,
        "dailyGoal" -> meter.dailyGoal,
        "weeklyGoal" -> meter.weeklyGoal
      )
    )

    meterCollection.flatMap(
      _.findAndUpdate(selector, updateModifier, fetchNewObject = true).map(_.result[Meter])
    )
  }
}
