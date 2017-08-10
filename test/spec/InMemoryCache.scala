package spec

import akka.Done
import net.sf.ehcache.Element
import play.api.cache.AsyncCacheApi

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class InMemoryCache (implicit ec : ExecutionContext) extends AsyncCacheApi {

  val cache = scala.collection.mutable.Map[String, Element]()

  def set(key: String, value: Any, expiration: Duration): Future[Done] = Future {
    val element = new Element(key, value)
    if (expiration._1 == 0) element.setEternal(true)
    element.setTimeToLive(expiration.toSeconds.toInt)
    cache.put(key, element)
    Done
  }

  def remove(key: String): Future[Done] = Future {
    cache -= key
    Done
  }

  def get[T: ClassTag](key: String): Future[Option[T]] = Future {
    cache.get(key).map(_.getObjectValue).asInstanceOf[Option[T]]
  }

  def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] = {
    get[A](key).flatMap {
      case Some(value) => Future.successful(value)
      case None => orElse.flatMap(value => set(key, value, expiration).map(_ => value))
    }
  }

  def removeAll(): Future[Done] = Future {
    cache.clear()
    Done
  }

}