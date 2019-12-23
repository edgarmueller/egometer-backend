package models.entry

import com.google.inject.Inject
import models.meter.{Meter, MeterDto, MetersDao}
import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MeterEntriesService @Inject()(
                                     metersDao: MetersDao,
                                     meterEntriesDao: MeterEntriesDao,
                                   )(implicit ec: ExecutionContext) {

  import models.JsonFormats._

  def findEntriesByYearAndMonth(year: Int, month: Int): Future[MeterEntriesEnvelopeDto] = {
    // TODO: validate year and month
    val fromDate = new DateTime(year, month, 1, 0, 0)
    val days = new DateTime()
      .withWeekyear(year)
      .withMonthOfYear(month)
      .dayOfMonth()
      .withMaximumValue()
      .getDayOfMonth
    val endDate = fromDate.plusDays(days)
    val selector = Json.obj(
      "date" -> Json.obj(
        "$gte" -> fromDate.toString,
        "$lte" -> endDate.toString
      )
    )
    findEntries(selector)
      .map(groupedEntries =>
        groupedEntries.map(x => MeterEntriesByMeterDto(x._1._id.map(_.stringify).getOrElse("not found"), MeterDto.toDto(x._1), x._2, None)).toSeq)
      .map(MeterEntriesEnvelopeDto)
  }

  def findEntriesByWeek(year: Int, week: Int): Future[MeterEntriesEnvelopeDto] = {
    Try {
      new DateTime()
        .withWeekyear(year)
        .withWeekOfWeekyear(week)
        .withDayOfWeek(1)
    }.fold(
      error => Future.failed(error),
      fromDate => {
        val endDate = fromDate.plusDays(7)
        val selector = Json.obj(
          "date" -> Json.obj(
            "$gte" -> fromDate.toString,
            "$lte" -> endDate.toString
          )
        )
        findEntries(selector)
          .map(groupedEntries => {
            groupedEntries.map(x =>
              MeterEntriesByMeterDto(x._1._id.map(_.stringify).getOrElse("not found"), MeterDto.toDto(x._1), x._2, this.calcProgress(x._1, x._2))
            ).toSeq
          })
          .map(MeterEntriesEnvelopeDto)
      })
  }

  private def findEntries(selector: JsObject): Future[Map[Meter, Seq[MeterEntryDto]]] = {
    meterEntriesDao
      .query(selector)
      .flatMap {
        entries => {
          val grouped: Map[String, Seq[MeterEntry]] = entries.seq.groupBy(_.meterId)
          val groupedEntries = grouped.mapValues(entries => entries.map(MeterEntryDto.toDto))
          Future.sequence(
            groupedEntries
              .map(grouped =>
                metersDao
                  .findById(grouped._1)
                  .flatMap {
                    case Some(meter) => Future.successful((meter, grouped._2))
                    case None => Future.failed(new IllegalStateException(s"Meter s${grouped._1} not found"))
                  }
              )
          ).map(x => x.toMap)
        }
      }
  }

  private def calcProgress(meter: Meter, entries: Seq[MeterEntryDto]): Option[Double] = {
    meter.weeklyGoal.map(goal => (meter, goal))
      .map(weeklyGoal => entries.count(e => this.goalAccomplished(weeklyGoal._1, e)).toDouble/weeklyGoal._2)
  }

  private def goalAccomplished(meter: Meter, entry: MeterEntryDto): Boolean = {
    entry.value match {
      case num: JsNumber =>   num.value.toInt > meter.dailyGoal.getOrElse(0)
      case b: JsBoolean => b.value
      case _ => true
    }
  }
}
