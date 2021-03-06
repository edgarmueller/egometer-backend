package modules

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Provider, Singleton}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import javax.inject.Inject
import net.codingwell.scalaguice.ScalaModule
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.Future

/**
 * The Guice module for the Quartz scheduler.
 */
class QuartzSchedulerModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bind[QuartzSchedulerExtension].toProvider[QuartzSchedulerProvider].in[Singleton]
  }
}

/**
 * Provides the Quartz scheduler.
 *
 * @param system    The actor system.
 * @param lifecycle The application lifecycle.
 */
@Singleton
class QuartzSchedulerProvider @Inject() (system: ActorSystem, lifecycle: ApplicationLifecycle)
  extends Provider[QuartzSchedulerExtension] {

  /**
   * Gets the Quartz scheduler.
   *
   * @return The Quartz scheduler instance.
   */
  override def get(): QuartzSchedulerExtension = {
    val scheduler = QuartzSchedulerExtension(system)
    Logger.info("Add stop hock for QuartzScheduler: " + scheduler.schedulerName)
    lifecycle.addStopHook { () =>
      Logger.info("Shutdown QuartzScheduler: " + scheduler.schedulerName)
      Future.successful(scheduler.shutdown())
    }

    scheduler
  }
}
