package zamblauskas.poller

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait Poller {

  def poll[Err, Val](
    interval: FiniteDuration,
    timeout: FiniteDuration,
    f: () => Future[Option[Either[Err, Val]]]
  ): Future[Either[Err, Val]]

}

object Poller {

  class TimeoutException(msg: String) extends Exception(msg)

}