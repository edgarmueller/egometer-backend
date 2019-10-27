package models.entry

import com.google.inject.Inject
import models.JsonFormats
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

class MeterEntriesDao @Inject()(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi){

  def meterEntryCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("entries"))


  /**
    * Retrieve all entries starting from the beginning of the month.
    * @param selector query object
    * @return
    */
  def query(selector: JsObject): Future[Seq[MeterEntry]] = {
    import JsonFormats.meterEntryFormat
    meterEntryCollection.flatMap(
      _.find[JsObject, MeterEntry](selector)
        .sort(Json.obj("date" -> 1))
        .cursor[MeterEntry](ReadPreference.primary)
        .collect[Seq](1000, Cursor.FailOnError[Seq[MeterEntry]]())
    )
  }

  /**
    * Upsert given entry.
    *
    * @param meterEntry the entry to updated/created
    * @return mongo's write result
    */
  def upsertEntry(meterEntry: MeterEntry): Future[Option[MeterEntry]] = {
    import JsonFormats.meterEntryFormat
    for {
      collection <- meterEntryCollection
      res <- collection.findAndUpdate(
        selector = Json.obj(
          "date" -> meterEntry.date,
          "meterId" -> meterEntry.meterId
        ),
        update = Json.obj(
          "$set" -> Json.obj(
            "date" -> meterEntry.date,
            "value" -> meterEntry.value,
            "meterId" -> meterEntry.meterId
          )
        ),
        fetchNewObject = true,
        upsert = true
      )
    } yield res.value.map(_.as[MeterEntry])
  }

  def deleteEntries(query: JsObject): Future[WriteResult] =
    meterEntryCollection.flatMap(_.delete().one(query))
}
