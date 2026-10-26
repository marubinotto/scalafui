package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import fui.Sub
import Game.*
import Main.*

class DamageSpec extends AnyFunSuite {
  private val board = Board(Vector("#######", "#S...E#", "#.....#", "#######"))
  private val base = State(board, board.start)
  private val right = Action.Move(Direction.Right)
  private def act(model: Model, action: Action): Model = update(Act(action), model)._1

  test("damage events identify both targets, defeat, and death without changing turn rules") {
    val game = base.copy(enemies = Vector(Enemy(7, Pos(2, 1))))
    val hit = stepWithEvents(game, right)
    assert(hit.state == Game.step(game, right))
    assert(hit.damage == Vector(Damage(Target.Goblin(7), Pos(2, 1), false), Damage(Target.Player, base.player, false)))
    val killed = stepWithEvents(hit.state, right)
    assert(killed.state.enemies.isEmpty && killed.state.hp == hit.state.hp)
    assert(killed.damage == Vector(Damage(Target.Goblin(7), Pos(2, 1), true)))
    val death = stepWithEvents(game.copy(hp = 1), Action.Wait)
    assert(death.state.status == Status.Lost)
    assert(death.damage == Vector(Damage(Target.Player, base.player, true)))
    assert(stepWithEvents(death.state, Action.Wait).damage.isEmpty)
  }

  test("no damage events for safe actions; healing does not hide an attack") {
    Seq(Action.Wait, right, Action.Move(Direction.Up)).foreach { action =>
      assert(stepWithEvents(base, action).damage.isEmpty)
    }
    val game = base.copy(hp = 5, items = Map(Pos(2, 1) -> Item.Potion), enemies = Vector(Enemy(0, Pos(3, 1))))
    val turn = stepWithEvents(game, right)
    assert(turn.state.hp == 8)
    assert(turn.damage == Vector(Damage(Target.Player, Pos(2, 1), false)))
  }

  test("multiple attacks share one flash; repeated damage replaces the timer and ignores stale cleanup") {
    val game = base.copy(enemies = Vector(Enemy(0, Pos(2, 1)), Enemy(1, Pos(1, 2))))
    assert(stepWithEvents(game, Action.Wait).damage.size == 2)
    val first = act(Model(Some(game)), Action.Wait)
    assert(first.flashes.size == 1 && Sub.toMap(subscriptions(first)).size == 1)
    val old = first.flashes(Target.Player).serial
    val second = act(first, Action.Wait)
    val latest = second.flashes(Target.Player).serial
    assert(latest > old)
    assert(Sub.toMap(subscriptions(first)).keySet.intersect(Sub.toMap(subscriptions(second)).keySet).isEmpty)
    assert(update(ClearFlash(Target.Player, old), second)._1 == second)
    val cleared = update(ClearFlash(Target.Player, latest), second)._1
    assert(cleared.game == second.game && cleared.flashes.isEmpty)
    assert(Sub.toMap(subscriptions(cleared)).isEmpty)
  }

  test("flashes follow living targets, defeated ghosts do not override occupants") {
    val game = base.copy(enemies = Vector(Enemy(0, Pos(2, 1), 2)))
    val killed = act(Model(Some(game)), right)
    assert(killed.game.get.enemies.isEmpty)
    assert(flashAt(killed, killed.game.get, Pos(2, 1)).exists(_.damage.defeated))
    val moved = act(killed, right)
    assert(moved.game.get.player == Pos(2, 1))
    assert(flashAt(moved, moved.game.get, Pos(2, 1)).isEmpty)
    val occupied = killed.game.get.copy(enemies = Vector(Enemy(1, Pos(2, 1))))
    assert(flashAt(killed, occupied, Pos(2, 1)).isEmpty)
    val hit = act(Model(Some(base.copy(enemies = Vector(Enemy(0, Pos(2, 1)))))), Action.Wait)
    val moving = act(hit, Action.Move(Direction.Down))
    assert(flashAt(moving, moving.game.get, Pos(1, 2)).nonEmpty)
    assert(flashAt(moving, moving.game.get, Pos(1, 1)).isEmpty)
  }

  test("descent and restart clear effects without reusing serials") {
    val hit = act(Model(Some(base.copy(enemies = Vector(Enemy(0, Pos(2, 1)))))), Action.Wait)
    val atExit = hit.copy(game = Some(hit.game.get.copy(player = Pos(4, 1), hasKey = true)))
    val descending = act(atExit, right)
    assert(descending.flashes.isEmpty && descending.effectSerial == hit.effectSerial)
    val arrived = update(FloorReady(42), descending)._1
    assert(arrived.flashes.isEmpty && arrived.effectSerial == hit.effectSerial)
    val restarted = update(Restart, hit)._1
    assert(restarted.game.isEmpty && restarted.flashes.isEmpty && restarted.effectSerial == hit.effectSerial)
    val ready = update(Ready(42), restarted)._1
    assert(ready.effectSerial == hit.effectSerial)
    val again = act(ready.copy(game = hit.game), Action.Wait)
    assert(again.flashes(Target.Player).serial > hit.flashes(Target.Player).serial)
    assert(update(ClearFlash(Target.Player, hit.flashes(Target.Player).serial), again)._1 == again)
  }
}
