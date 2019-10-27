package models.entry

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