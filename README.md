# About

Every now and then I come across a task where I have to submit some job to a 3rd party service and then await for completion by periodically polling job status.

This is all it does - you give a polling function and get back a future which completes when you return either an error or a value.

Backed by Akka, no blocking obviously.

# Example

```scala
  import java.util.concurrent.TimeUnit.{MINUTES, SECONDS}
  import akka.actor.ActorSystem
  import zamblauskas.poller.{AkkaPoller, Poller}
  import scala.concurrent.duration.FiniteDuration
  import scala.concurrent.{ExecutionContext, Future}


  // we need an actor system for Poller implementation
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher

  // hypothetical 3rd party API,
  // we want to poll until status is either Failed or Completed
  sealed trait Status
  case object Running extends Status
  case object Failed extends Status
  case object Completed extends Status
  def some3rdPartyApiCall: Future[Status] = Future.successful(Completed)

  // create a Poller
  val poller: Poller = new AkkaPoller()

  // await for the completion
  val done: Future[Either[String, Unit]] = poller.poll(
    interval = FiniteDuration(5, SECONDS),
    timeout = FiniteDuration(2, MINUTES),
    () => {
      some3rdPartyApiCall.map {
        case Running =>   None
        case Failed  =>   Some(Left("3rd party failed"))
        case Completed => Some(Right(()))
      }
    }
  )
```
