package modules

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.util.CacheLayer
import com.mohiva.play.silhouette.impl.util.PlayCacheLayer
import net.codingwell.scalaguice.ScalaModule

/**
 * The base Guice module.
 */
class BaseModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bind[java.time.Clock].toInstance(java.time.Clock.systemUTC())
    bind[CacheLayer].to[PlayCacheLayer]

  }
}
