package models.entry

import play.api.libs.json.{JsString, JsValue}

case class MeterEntryDao(
                          id: Option[String],
                          meterId: String,
                          value: JsValue,
                          date: JsString
                        )

object MeterEntryDao {
  def toDao(meterEntry: MeterEntry): MeterEntryDao = {
    MeterEntryDao(
      meterEntry._id.map(_.stringify),
      meterEntry.meterId,
      meterEntry.value,
      meterEntry.date
    )
  }
}