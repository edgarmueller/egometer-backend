package models.meter

import java.util.UUID

case class MeterDao(
                  id: Option[String],
                  schemaId: String,
                  name: String,
                  widget: String,
                  color: String,
                  userId: Option[UUID],
                  icon: Option[String],
                  dailyGoal: Option[Int],
                  weeklyGoal: Option[Int]
                )

object MeterDao {
  def toDao(meter: Meter): MeterDao = {
    MeterDao(
      meter._id.map(_.stringify),
      meter.schemaId,
      meter.name,
      meter.widget,
      meter.color,
      meter.userId,
      meter.icon,
      meter.dailyGoal,
      meter.weeklyGoal
    )
  }
}