package fui

import scala.concurrent.duration.*
import scala.scalajs.js
import cats.effect.{Deferred, IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite

class SubSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def stream[A](sub: Sub[A]): Stream[IO, A] = Sub.toMap(sub).values.head

  test("callback emissions during registration are retained and cleanup runs once") {
    (for {
      cleanups <- Ref.of[IO, Int](0)
      sub = Sub.fromCallback[Int]("callback") { emit =>
        IO { emit(1); emit(2) }.as(cleanups.update(_ + 1))
      }
      result <- stream(sub.map(_ * 10)).take(2).compile.toList.timeout(2.seconds)
      count <- cleanups.get
    } yield {
      assert(result == List(10, 20))
      assert(count == 1)
    }).unsafeToFuture()
  }

  test("cancellation during asynchronous registration waits for cleanup") {
    (for {
      started <- Deferred[IO, Unit]
      acquired <- Deferred[IO, Unit]
      cleanups <- Ref.of[IO, Int](0)
      sub = Sub.fromCallback[Int]("async") { _ =>
        (started.complete(()) *> IO.sleep(30.millis) *> acquired.complete(())).as(cleanups.update(_ + 1))
      }
      fiber <- stream(sub).compile.drain.start
      _ <- started.get
      _ <- fiber.cancel.timeout(2.seconds)
      ready <- acquired.tryGet
      count <- cleanups.get
    } yield {
      assert(ready.contains(()))
      assert(count == 1)
    }).unsafeToFuture()
  }

  test("registration failure reaches the stream consumer") {
    val failure = new RuntimeException("registration failed")
    stream(Sub.fromCallback[Int]("failed")(_ => IO.raiseError(failure)))
      .compile.drain.attempt.timeout(2.seconds)
      .map(result => assert(result == Left(failure))).unsafeToFuture()
  }

  test("timeout emits once and every can be canceled and restarted") {
    (for {
      once <- stream(Sub.timeout(5.millis, "done", "timeout")).compile.toList
      ticks <- Ref.of[IO, Int](0)
      first <- Deferred[IO, Unit]
      fiber <- stream(Sub.every(5.millis, "tick"))
        .evalMap(_ => ticks.update(_ + 1) *> first.complete(()).void).compile.drain.start
      _ <- first.get.timeout(2.seconds)
      _ <- fiber.cancel
      stopped <- ticks.get
      _ <- IO.sleep(20.millis)
      after <- ticks.get
      restarted <- stream(Sub.every(5.millis, "tick")).take(2).compile.toList.timeout(2.seconds)
    } yield {
      assert(once == List("done"))
      assert(stopped > 0 && after == stopped)
      assert(restarted.size == 2)
    }).unsafeToFuture()
  }

  test("DOM event listeners are removed when their stream is canceled") {
    (for {
      ready <- Deferred[IO, Unit]
      received <- Deferred[IO, String]
      conversions <- Ref.of[IO, Int](0)
      // Instrument the event target to know when registration has completed.
      listener = new Array[js.Function1[dom.Event, Unit]](1)
      removed = new Array[Boolean](1)
      target = js.Dynamic.literal(
        addEventListener = ((_: String, f: js.Function1[dom.Event, Unit]) => {
          listener(0) = f
          ready.complete(()).unsafeRunAndForget()
        }): js.Function2[String, js.Function1[dom.Event, Unit], Unit],
        removeEventListener = ((_: String, f: js.Function1[dom.Event, Unit]) => {
          assert(listener(0) == f)
          removed(0) = true
          listener(0) = null
        }): js.Function2[String, js.Function1[dom.Event, Unit], Unit]
      ).asInstanceOf[dom.EventTarget]
      sub = Sub.fromEvent[dom.Event, String]("custom", target)(e => Some(e.`type`))
      fiber <- stream(sub).evalMap(msg => conversions.update(_ + 1) *> received.complete(msg).void).compile.drain.start
      _ <- ready.get.timeout(2.seconds)
      _ <- IO(listener(0)(js.Dynamic.literal(`type` = "custom").asInstanceOf[dom.Event]))
      value <- received.get.timeout(2.seconds)
      _ <- fiber.cancel
      count <- conversions.get
    } yield {
      assert(value == "custom" && count == 1)
      assert(removed(0) && listener(0) == null)
    }).unsafeToFuture()
  }
}
