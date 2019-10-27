package models.auth

case class SignInInfo(email: String, password: String, rememberMe: Boolean = false)