package modules

import jobs.auth.{AuthTokenCleaner, Scheduler}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The job module.
 */
class JobModule extends ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bindActor[AuthTokenCleaner](AuthTokenCleaner.Name)
    bind[Scheduler].asEagerSingleton()
  }
}
