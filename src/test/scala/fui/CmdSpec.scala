package fui.tests

import cats.effect.{Deferred, IO, Outcome, Ref}
import cats.effect.unsafe.implicits.global
import fui.*
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.duration.*

class CmdSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  test("constructors are lazy and distinguish values from optional notifications") {
    var runs = 0
    val emit = IO { runs += 1; 7 }.toCmd
    val exec = Cmd.exec(IO { runs += 1; 9 })
    val optional = Cmd.optional(IO { runs += 1; Some(11) })
    assert(runs == 0)
    (for {
      a <- emit.io
      b <- exec.io
      c <- optional.io
      d <- IO.pure(Option.empty[Int]).toCmd.io
      e <- IO.unit.toCmd.io
      f <- Cmd.optional(IO.pure(Option.empty[Int])).io
    } yield {
      assert(runs == 3)
      assert(a.contains(7) && b.isEmpty && c.contains(11))
      assert(d.contains(None) && e.contains(()) && f.isEmpty)
    }).unsafeToFuture()
  }

  test("all constructors preserve failure and cancellation") {
    val constructors: List[IO[Int] => Cmd.One[?]] = List(
      _.toCmd,
      io => Cmd.exec(io),
      io => Cmd.optional(io.map(Some(_)))
    )
    val error = new RuntimeException("failed")
    constructors.foldLeft(IO.unit) { (previous, wrap) =>
      previous *> (for {
        failed <- wrap(IO.raiseError[Int](error)).io.attempt
        started <- Deferred[IO, Unit]
        cleaned <- Ref.of[IO, Boolean](false)
        fiber <- wrap(started.complete(()) *> IO.never[Int]
          .onCancel(cleaned.set(true))).io.start
        _ <- started.get
        _ <- fiber.cancel
        outcome <- fiber.join
        released <- cleaned.get
      } yield {
        assert(failed == Left(error))
        assert(outcome match {
          case Outcome.Canceled() => true
          case _ => false
        })
        assert(released)
      })
    }.as(succeed).timeout(2.seconds).unsafeToFuture()
  }

  test("IO sequencing, parent mapping and batches retain execution policies") {
    case class Parent(value: Int)
    def update(model: Int): (Int, Cmd[Int]) =
      (model + 1, (IO.unit *> IO.pure(model)).toCmd)
    val child = IO.pure(1).toCmd.debounce("search", 500)
    val mapped = child.map(Parent.apply)
    val discarded = child.toNone[Parent]
    val batch: Cmd[Parent] = Cmd.Batch(mapped, Cmd.exec(IO.unit)) ++ Cmd.none
    val empty = Cmd.none[Parent]
    assert(empty.cmds.isEmpty)
    assert((empty ++ empty) == empty)
    assert(mapped.debounce == child.debounce)
    assert(discarded.debounce == child.debounce)
    assert(batch.map(_.value).isInstanceOf[Cmd.Batch[?]])
    (for {
      value <- mapped.io
      none <- discarded.io
      sequenced <- update(2)._2.asInstanceOf[Cmd.One[Int]].io
    } yield {
      assert(value.contains(Parent(1)) && none.isEmpty)
      assert(sequenced.contains(2))
    }).unsafeToFuture()
  }
}
