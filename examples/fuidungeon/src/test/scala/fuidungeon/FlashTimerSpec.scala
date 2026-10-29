package fuidungeon

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*
import org.scalatest.funsuite.AsyncFunSuite
import marubinotto.fui.Sub
import Game.*
import Main.*

class FlashTimerSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  test("flash subscription emits its matching cleanup after 200 ms") {
    val model = Model(flashes = Map(Target.Player -> Flash(Damage(Target.Player, Pos(1, 1), false), 9)), effectSerial = 9)
    (for {
      start <- IO.monotonic
      msg <- Sub.toMap(subscriptions(model)).values.head.compile.lastOrError.timeout(2.seconds)
      end <- IO.monotonic
    } yield {
      assert(end - start >= 200.millis)
      assert(msg == ClearFlash(Target.Player, 9))
      assert(update(msg, model)._1.flashes.isEmpty)
    }).unsafeToFuture()
  }
}
