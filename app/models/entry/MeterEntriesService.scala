package models.entry

import com.google.inject.Inject
import models.meter.{Meter, MeterDto, MetersDao}
import org.joda.time.{DateTime, Days}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MeterEntriesService @Inject()(
                                     metersDao: MetersDao,
                                     meterEntriesDao: MeterEntriesDao,
                                   )(implicit ec: ExecutionContext) {

  val DateFormat: DateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-dd")

  def findEntriesByYearAndMonth(year: Int, month: Int): Future[Seq[MeterEntriesByMeterDto]] = {
    Try {
      new DateTime(year, month, 1, 0, 0)
    }.fold(
      error => Future.failed(error),
      fromDate => {
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
            groupedEntries.map { case (meter, entries) =>
              MeterEntriesByMeterDto(
                meter._id.map(_.stringify).getOrElse("not found"),
                MeterDto.toDto(meter),
                entries,
                this.calcProgress(meter, entries, fromDate, endDate)
              )
            }.toSeq)
      }
    )
  }

  def findEntriesByYearAndWeek(year: Int, week: Int): Future[Seq[MeterEntriesByMeterDto]] = {
    Try {
      new DateTime()
        .withWeekyear(year)
        .withWeekOfWeekyear(week)
        .withDayOfWeek(1)
    }.fold(
      error => Future.failed(error),
      fromDate => {
        val endDate = fromDate.plusDays(6)
        val selector = Json.obj(
          "date" -> Json.obj(
            "$gte" -> DateFormat.print(fromDate),
            "$lte" -> DateFormat.print(endDate)
          )
        )
        findEntries(selector)
          .map(groupedEntries => {
            groupedEntries.map { case (meter, entries) =>
              MeterEntriesByMeterDto(
                meter._id.map(_.stringify).getOrElse("not found"),
                MeterDto.toDto(meter),
                entries,
                this.calcProgress(meter, entries, fromDate, endDate)
              )
            }.toSeq
          })
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
          ).map(_.toMap)
        }
      }
  }

  private def calcProgress(meter: Meter, entries: Seq[MeterEntryDto], fromDate: DateTime, toDate: DateTime): Option[Double] = {
    val rangeInDays = Days.daysBetween(fromDate.withTimeAtStartOfDay(), toDate.withTimeAtStartOfDay()).getDays
    meter.weeklyGoal
      .map(goal => {
        val actualGoal = (2 * rangeInDays.toDouble)/goal
        entries.count(e => this.goalAccomplished(meter, e))/actualGoal
      })
  }

  private def goalAccomplished(meter: Meter, entry: MeterEntryDto): Boolean = {
    entry.value match {
      case num: JsNumber =>   num.value.toInt > meter.dailyGoal.getOrElse(0)
      case b: JsBoolean => b.value
      case _ => true
    }
  }
}
