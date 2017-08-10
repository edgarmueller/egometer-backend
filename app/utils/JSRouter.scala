package utils

import javax.inject.Inject
import play.api.mvc.{Call, RequestHeader}
import play.api.{Configuration, Environment, Mode}

/**
 * A helper class which allows to build JS routes, based on the current environment.
 *
 * @param env  The Play environment.
 * @param conf The Play configuration.
 */
class JSRouter @Inject()(env: Environment, conf: Configuration) {

  /**
   * Build a absolute URL for the given route.
   *
   * @param route   The route to create the absolute URL.
   * @param request The current request header.
   * @return The absolute URL for the given route.
   */
  def absoluteURL(route: String)(implicit request: RequestHeader): String = {
    env.mode match {
        // TODO
//      case Mode.Prod => Call("GET", route).absoluteURL()
      case _ =>
        val host = conf.getOptional[String]("ui.url")
          .getOrElse(throw new RuntimeException("Cannot get `ui.url` from config"))

        host.stripSuffix("/") + "/" + route.stripPrefix("/")
    }
  }
}
