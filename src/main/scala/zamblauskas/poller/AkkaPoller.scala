package zamblauskas.poller

import java.util.UUID

import akka.actor.{Actor, ActorSystem, Cancellable, Props}
import zamblauskas.poller.AkkaPoller.Worker
import zamblauskas.poller.Poller.TimeoutException

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class AkkaPoller(implicit system: ActorSystem) extends Poller {
  override def poll[T](
    name: String,
    interval: FiniteDuration,
    timeout: FiniteDuration,
    f: () => Future[Option[T]]
  ): Future[T] = {
    val promise = Promise[T]()
    system.actorOf(Props(new Worker(name, interval, timeout, f, promise)), s"poller-${UUID.randomUUID().toString}")
    promise.future
  }
}

object AkkaPoller {

  private class Worker[T](
    name: String,
    interval: FiniteDuration,
    timeout: FiniteDuration,
    poll: () => Future[Option[T]],
    promise: Promise[T]
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
    private case class Complete(value: T)
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
        promise.failure(new TimeoutException(s"$name did not complete within $timeout"))
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
