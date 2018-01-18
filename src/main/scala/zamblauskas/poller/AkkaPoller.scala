package zamblauskas.poller

import akka.actor.{Actor, ActorSystem, Props}
import zamblauskas.poller.AkkaPoller.Worker
import zamblauskas.poller.Poller.TimeoutException

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class AkkaPoller(implicit system: ActorSystem) extends Poller {
  override def poll[L, R](
    interval: FiniteDuration,
    timeout: FiniteDuration,
    f: () => Future[Option[Either[L, R]]]
  ): Future[Either[L, R]] = {
    val promise = Promise[Either[L, R]]()
    system.actorOf(Props(new Worker(interval, timeout, f, promise)), "poller")
    promise.future
  }
}

object AkkaPoller {

  private class Worker[L, R](
    interval: FiniteDuration,
    timeout: FiniteDuration,
    poll: () => Future[Option[Either[L, R]]],
    promise: Promise[Either[L, R]]
  ) extends Actor {

    private implicit val ec: ExecutionContext = context.dispatcher

    private val pollCancellable = context.system.scheduler.schedule(
      initialDelay = FiniteDuration(0, SECONDS),
      interval,
      self,
      Poll
    )

    private val timeoutCancellable = context.system.scheduler.scheduleOnce(
      timeout,
      self,
      Timeout
    )

    private case object Poll
    private case object Timeout
    private case class Complete(value: Either[L, R])
    private case class Fail(t: Throwable)

    override def receive: Receive = {
      case Poll =>
        poll().onComplete {
          case Failure(f) =>
            self ! Fail(f)
          case Success(None) =>
            // do nothing
          case Success(Some(v)) =>
            self ! Complete(v)
        }

      case Timeout =>
        promise.failure(new TimeoutException(s"$timeout has passed, yet no result - giving up"))
        stop()

      case Complete(v) =>
        promise.success(v)
        stop()

      case Fail(t) =>
        promise.failure(t)
        stop()
    }

    private def stop(): Unit = {
      pollCancellable.cancel()
      timeoutCancellable.cancel()
      context.stop(self)
    }
  }
}
