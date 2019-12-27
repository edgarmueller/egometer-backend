package models.entry

import com.eclipsesource.schema.SchemaType
import com.google.inject.Inject
import controllers.common.WithValidator
import models.meter.{Meter, MeterDto, MetersDao}
import models.schema.{Schema, SchemasDao}
import org.joda.time.{DateTime, Days}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MeterEntriesService @Inject()(
                                     metersDao: MetersDao,
                                     schemaDao: SchemasDao,
                                     meterEntriesDao: MeterEntriesDao
                                   )(implicit ec: ExecutionContext)
  extends WithValidator {

  import com.eclipsesource.schema.drafts.Version7._

  val DateFormat: DateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-dd")

  def deleteEntryById(entryId: String): Future[Boolean] = {
    meterEntriesDao
      .deleteEntries(Json.obj("_id" -> Json.obj("$oid" -> entryId)))
      .map(_.ok)
  }

  def findById(entryId: String): Future[Option[MeterEntryDto]] = {
    meterEntriesDao
      .query(Json.obj("_id" -> Json.obj("$oid" -> entryId)))
      .map(_.headOption)
      .map(_.map(MeterEntryDto.toDto))
  }

  def findEntriesByYearAndMonth(year: Int, month: Int): Future[Seq[MeterEntriesByMeterDto]] = {
    checkValidMonth(year, month)
      .fold(
        error => Future.failed(error),
        fromDate => {
          val days = getDaysOfMonth(year, month)
          val toDate = fromDate.plusDays(days)
          val selector = createDateRangeQuery(fromDate, toDate)
          findEntries(selector)
            .map(groupedEntries => this.calcProgress(groupedEntries, fromDate, toDate)
          )
        }
      )
  }

  def findEntriesByYearAndWeek(year: Int, week: Int): Future[Seq[MeterEntriesByMeterDto]] = {
    checkValidWeek(year, week)
      .fold(
        error => Future.failed(error),
        fromDate => {
          val toDate = fromDate.plusDays(6)
          val selector = createDateRangeQuery(fromDate, toDate)
          findEntries(selector)
            .map(groupedEntries => this.calcProgress(groupedEntries, fromDate, toDate))
        })
  }

  def upsertEntry(entry: MeterEntry)(schemaId: String): Future[Either[Seq[(JsPath, Seq[JsonValidationError])], Option[MeterEntryDto]]] = {
    schemaDao
      .findById(schemaId)
      .flatMap(maybeSchema => {
        maybeSchema
          .flatMap(parseSchema)
          .map(upsertValidEntry(entry))
          .getOrElse(Future(Right(None)))
      })
  }

  def deleteByMeterId(meterId: String) = {
    meterEntriesDao
      .deleteEntries(Json.obj("meterId" -> meterId))
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

  private def calcProgress(groupedEntries: Map[Meter, Seq[MeterEntryDto]], fromDate: DateTime, toDate: DateTime) = {
    groupedEntries.map { case (meter, entries) =>
      MeterEntriesByMeterDto(
        meter._id.map(_.stringify).getOrElse("not found"),
        MeterDto.toDto(meter),
        entries,
        this.calcProgressForMeter(meter, entries, fromDate, toDate)
      )
    }.toSeq
  }

  private def createDateRangeQuery(fromDate: DateTime, toDate: DateTime) =
    Json.obj(
      "date" -> Json.obj(
        "$gte" -> DateFormat.print(fromDate),
        "$lte" -> DateFormat.print(toDate)
      )
    )

  private def checkValidMonth(year: Int, month: Int) = Try {
    new DateTime(year, month, 1, 0, 0)
  }

  private def checkValidWeek(year: Int, week: Int) =
    Try {
      new DateTime()
        .withWeekyear(year)
        .withWeekOfWeekyear(week)
        .withDayOfWeek(1)
    }

  private def getDaysOfMonth(year: Int, month: Int): Int =
    new DateTime()
      .withWeekyear(year)
      .withMonthOfYear(month)
      .dayOfMonth()
      .withMaximumValue()
      .getDayOfMonth

  private def calcProgressForMeter(meter: Meter, entries: Seq[MeterEntryDto], fromDate: DateTime, toDate: DateTime): Option[Double] = {
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

  private def upsertValidEntry(entry: MeterEntry)(schema: SchemaType): Future[Either[Seq[(JsPath, Seq[JsonValidationError])], Option[MeterEntryDto]]] = {
    validator.validate(schema)(entry.value)
      .fold(
        errors => Future.successful(Left(errors)),
        _ => meterEntriesDao.upsertEntry(entry)
          .map { upsertedEntry => Right(upsertedEntry.map(MeterEntryDto.toDto)) }
      )
  }

  private def parseSchema(meterSchema: Schema): Option[SchemaType] = {
    Json.fromJson[SchemaType](meterSchema.schema).asOpt
  }
}
