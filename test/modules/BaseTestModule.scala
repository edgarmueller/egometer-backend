package modules

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.util.CacheLayer
import com.mohiva.play.silhouette.impl.util.PlayCacheLayer
import spec.InMemoryCache
import net.codingwell.scalaguice.ScalaModule
import play.api.cache.AsyncCacheApi

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * The base Guice module.
 */
class BaseTestModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    val inMemoryCache = new InMemoryCache()
    bind[java.time.Clock].toInstance(java.time.Clock.systemUTC())
    // replace cache implementation to avoid EhCacheException, see
    // https://stackoverflow.com/questions/39453838/play-scala-2-5-testing-classes-injecting-cache-leads-to-an-error
    bind[AsyncCacheApi].toInstance(inMemoryCache)
//    bind[CacheLayer].toInstance(new PlayCacheLayer(inMemoryCache))
  }
}
