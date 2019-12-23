package models.entry

import models.meter.MeterDto
import play.api.libs.json.{JsString, JsValue}

case class MeterEntryDto(
                          id: Option[String],
                          meterId: String,
                          value: JsValue,
                          date: JsString
                        )

object MeterEntryDto {
  def toDto(meterEntry: MeterEntry): MeterEntryDto = {
    MeterEntryDto(
      meterEntry._id.map(_.stringify),
      meterEntry.meterId,
      meterEntry.value,
      meterEntry.date
    )
  }
}

case class MeterEntriesByMeterDto(
                                   meterId: String,
                                   meter: MeterDto,
                                   entries: Seq[MeterEntryDto],
                                   progress: Option[Double]
                                 )

case class MeterEntriesEnvelopeDto(meters: Seq[MeterEntriesByMeterDto])
