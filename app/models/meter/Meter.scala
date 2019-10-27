package models.meter

import java.util.UUID

import reactivemongo.bson.BSONObjectID

case class Meter(
                  _id: Option[BSONObjectID],
                  schemaId: String,
                  name: String,
                  widget: String,
                  color: String,
                  userId: Option[UUID],
                  icon: Option[String],
                  dailyGoal: Option[Int],
                  weeklyGoal: Option[Int]
                )
