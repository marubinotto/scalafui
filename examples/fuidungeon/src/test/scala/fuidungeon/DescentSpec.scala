package fuidungeon

import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite
import fui.Cmd
import Game.*
import Main.*

class DescentSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  test("one descent command is emitted; pending actions, restart and unrelated seeds are ignored") {
    val board = Board(Vector("#####", "#S.E#", "#####"))
    val game = State(board, Pos(2, 1), hp = 3, floor = 2, hasKey = true, turns = 10)
    val (pending, command) = update(Act(Action.Move(Direction.Right)), Model(Some(game)))
    assert(pending.game.exists(s => s.status == Status.Descending && s.turns == 11))
    val (waited, waitCommand) = update(Act(Action.Wait), pending)
    val (moved, moveCommand) = update(Act(Action.Move(Direction.Left)), pending)
    val (restarted, restartCommand) = update(Restart, pending)
    assert(waited == pending && moved == pending && restarted == pending)
    assert(update(Ready(99), pending)._1 == pending)
    assert(update(FloorReady(99), Model())._1 == Model())
    assert(update(FloorReady(99), Model(Some(game)))._1.game.contains(game))

    assert(waitCommand == Cmd.none && moveCommand == Cmd.none && restartCommand == Cmd.none)

    command.asInstanceOf[Cmd.One[Msg]].io.map { seedMessage =>
      assert(seedMessage.exists(_.isInstanceOf[FloorReady]))
      val arrived = update(seedMessage.get, pending)._1
      val next = arrived.game.get
      assert(next.floor == 3 && next.hp == 3 && next.turns == 11)
      assert(next.status == Status.Playing && !next.hasKey && next.player == next.board.start)
      assert(update(seedMessage.get, arrived)._1 == arrived)
      val lost = arrived.copy(game = Some(next.copy(hp = 0, status = Status.Lost)))
      assert(update(FloorReady(99), lost)._1 == lost)
      assert(update(Act(Action.Wait), lost)._1 == lost)
    }.unsafeToFuture()
  }
}
