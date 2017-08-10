package models

import java.util.UUID

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import utils.auth.Roles.{Role, UserRole}

case class User(
  id: UUID,
  loginInfo: Seq[LoginInfo],
  name: Option[String],
  email: Option[String],
  avatarURL: Option[String],
  registration: Registration,
  settings: Settings,
  role: Role = UserRole,
 ) extends Identity

