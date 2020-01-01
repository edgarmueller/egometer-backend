package models.meter

import com.google.inject.Inject
import controllers.common.WithValidator
import models.schema.SchemasDao
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class MetersService @Inject()(
                               metersDao: MetersDao,
                               schemaDao: SchemasDao,
                             )(implicit ec: ExecutionContext)
  extends WithValidator {

  def findById(meterId: String): Future[Option[MeterDto]] = {
    metersDao.findById(meterId)
      .map(_.map(MeterDto.toDto))
  }

  def findByUserId(userId: String): Future[Seq[MeterDto]] = {
    metersDao
      .query(Json.obj("userId" -> userId))
      .map(_.map(MeterDto.toDto))
  }

  def findByUserIdAndMeterId(userId: String, meterId: String): Future[Option[MeterDto]] = {
    metersDao
      .queryFirst(
        Json.obj(
          "userId" -> userId,
          "_id" -> Json.obj("$oid" -> meterId)
        )
      )
      .map(_.map(MeterDto.toDto))
  }

  def deleteById(meterId: String): Future[Option[MeterDto]] = {
    metersDao
      .deleteMeter(Json.obj("_id" -> Json.obj("$oid" -> meterId)))
      .map(_.map(MeterDto.toDto))
  }

  def updateById(meterId: String, meter: Meter): Future[Option[MeterDto]] = {
    metersDao
      .updateMeter(
        Json.obj("_id" -> Json.obj("$oid" -> meterId)),
        meter
      ).map(_.map(MeterDto.toDto))
  }

  def addMeter(meter: Meter): Future[Option[MeterDto]] = {
    metersDao
      .addMeter(meter)
      .map(res => if (res.ok) Some(MeterDto.toDto(meter)) else None)
  }
}
