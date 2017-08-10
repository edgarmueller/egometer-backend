package models.auth

import models.User
import org.joda.time.DateTime

case class SignInToken(user: User, token: String, expiry: DateTime)