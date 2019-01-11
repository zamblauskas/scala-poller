package zamblauskas.poller

import java.util.UUID

import akka.actor.{Actor, ActorSystem, Cancellable, Props}
import zamblauskas.poller.AkkaPoller.Worker
import zamblauskas.poller.Poller.TimeoutException

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class AkkaPoller(implicit system: ActorSystem) extends Poller {
  override def poll[L, R](
    interval: FiniteDuration,
    timeout: FiniteDuration,
    f: () => Future[Option[Either[L, R]]]
  ): Future[Either[L, R]] = {
    val promise = Promise[Either[L, R]]()
    system.actorOf(Props(new Worker(interval, timeout, f, promise)), s"poller-${UUID.randomUUID().toString}")
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

    private var scheduleCancellable = Option.empty[Cancellable]

    private val timeoutCancellable = context.system.scheduler.scheduleOnce(
      timeout,
      self,
      Timeout
    )

    self ! Schedule

    private case object Poll
    private case object Timeout
    private case object Schedule
    private case class Complete(value: Either[L, R])
    private case class Fail(t: Throwable)

    override def receive: Receive = {
      case Poll =>
        poll().onComplete {
          case Failure(f) =>
            self ! Fail(f)
          case Success(None) =>
            self ! Schedule
          case Success(Some(v)) =>
            self ! Complete(v)
        }

      case Schedule =>
        scheduleCancellable = Some(context.system.scheduler.scheduleOnce(
          interval,
          self,
          Poll
        ))

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
      scheduleCancellable.foreach(_.cancel())
      timeoutCancellable.cancel()
      context.stop(self)
    }
  }
}
