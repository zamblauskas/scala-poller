package zamblauskas.poller

import java.util.concurrent.TimeUnit.{MINUTES, SECONDS}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AkkaPollerSpec
  extends TestKit(ActorSystem("AkkaPollerSpec"))
    with FunSpecLike
    with Matchers
    with TypeCheckedTripleEquals
    with ScalaFutures
    with BeforeAndAfterAll
{

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val poller = new AkkaPoller()

  type Result = String

  private def mutablePoll(results: Stream[Future[Option[Result]]]): () => Future[Option[Result]] = new Function0[Future[Option[Result]]] {
    private var internal = results

    override def apply(): Future[Option[Result]] = {
      internal match {
        case h #:: t =>
          internal = t
          h
        case Stream.Empty =>
          throw new Exception("No more results")
      }
    }
  }

  private val testTimeout = Timeout(Span(1, Minutes))

  describe("AkkaPoller") {

    it("should return value") {
      val poll = mutablePoll(Stream(
        Future.successful(None),
        Future.successful(None),
        Future.successful(Some("result"))
      ))

      val future = poller.poll(
        name = "test",
        interval = FiniteDuration(1, SECONDS),
        timeout = FiniteDuration(1, MINUTES),
        poll
      )

      whenReady(future, testTimeout) { _ should === ("result") }
    }

    it("should return exception") {
      val exception = new Exception("some message")

      val poll = mutablePoll(Stream(
        Future.successful(None),
        Future.successful(None),
        Future.failed(exception)
      ))

      val future = poller.poll(
        name = "test",
        interval = FiniteDuration(1, SECONDS),
        timeout = FiniteDuration(1, MINUTES),
        poll
      )

      whenReady(future.failed, testTimeout) { _ should === (exception) }
    }
  }

  it("should timeout") {
    val poll = mutablePoll(Stream.continually(Future.successful(None)))

    val future = poller.poll(
      name = "test",
      interval = FiniteDuration(1, SECONDS),
      timeout = FiniteDuration(10, SECONDS),
      poll
    )

    whenReady(future.failed, testTimeout) { _ shouldBe a[Poller.TimeoutException] }
  }

  it("should not overlap") {
    val poll = mutablePoll(Stream(
      Future {
        Thread.sleep(5000)
        Some("result 1")
      },
      Future.successful(Some("result 2"))
    ))

    val future = poller.poll(
      name = "test",
      interval = FiniteDuration(1, SECONDS),
      timeout = FiniteDuration(10, SECONDS),
      poll
    )

    whenReady(future, testTimeout) { _ should === ("result 1") }
  }
}
