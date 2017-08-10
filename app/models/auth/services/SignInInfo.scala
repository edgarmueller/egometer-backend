package models.auth.services

case class SignInInfo(email: String, password: String, rememberMe: Boolean = false)