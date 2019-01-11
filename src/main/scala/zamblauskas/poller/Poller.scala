package zamblauskas.poller

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait Poller {
  def poll[T](
    name: String,
    interval: FiniteDuration,
    timeout: FiniteDuration,
    f: () => Future[Option[T]]
  ): Future[T]
}

object Poller {
  class TimeoutException(msg: String) extends Exception(msg)
}