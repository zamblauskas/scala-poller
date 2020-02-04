package zamblauskas.poller

import java.util.concurrent.TimeUnit.{MINUTES, SECONDS}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minutes, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AkkaPollerSpec
  extends TestKit(ActorSystem("AkkaPollerSpec"))
    with AnyFunSpecLike
    with Matchers
    with TypeCheckedTripleEquals
    with ScalaFutures
    with BeforeAndAfterAll
{

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val poller = new AkkaPoller()

  private case object Err
  private case object Val

  private type Result = Either[Err.type, Val.type]

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

    it("should return right value") {
      val poll = mutablePoll(Stream(
        Future.successful(None),
        Future.successful(None),
        Future.successful(Some(Right(Val)))
      ))

      val future = poller.poll(
        interval = FiniteDuration(1, SECONDS),
        timeout = FiniteDuration(1, MINUTES),
        poll
      )

      whenReady(future, testTimeout) { _ should === (Right(Val)) }
    }

    it("should return left value") {
      val poll = mutablePoll(Stream(
        Future.successful(None),
        Future.successful(None),
        Future.successful(Some(Left(Err)))
      ))

      val future = poller.poll(
        interval = FiniteDuration(1, SECONDS),
        timeout = FiniteDuration(1, MINUTES),
        poll
      )

      whenReady(future, testTimeout) { _ should === (Left(Err)) }
    }

    it("should return exception") {
      val exception = new Exception("some message")

      val poll = mutablePoll(Stream(
        Future.successful(None),
        Future.successful(None),
        Future.failed(exception)
      ))

      val future = poller.poll(
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
        Some(Right(Val))
      },
      Future.successful(Some(Left(Err)))
    ))

    val future = poller.poll(
      interval = FiniteDuration(1, SECONDS),
      timeout = FiniteDuration(10, SECONDS),
      poll
    )

    whenReady(future, testTimeout) { _ should === (Right(Val)) }
  }
}
