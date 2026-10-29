package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import marubinotto.fui.Sub
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
    assert(hit.damage == Vector(Damage(Target.Monster(7), Pos(2, 1), false, Some(EnemyKind.Goblin)), Damage(Target.Player, base.player, false)))
    val killed = stepWithEvents(hit.state, right)
    assert(killed.state.enemies.isEmpty && killed.state.hp == hit.state.hp)
    assert(killed.damage == Vector(Damage(Target.Monster(7), Pos(2, 1), true, Some(EnemyKind.Goblin))))
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

  private def advance(m: Model): Model = update(Advance(m.playback.get._1), m)._1

  test("attacks replay separately, ignore input, and reject stale timers") {
    val game = base.copy(enemies = Vector(Enemy(0, Pos(2, 1)), Enemy(1, Pos(1, 2))))
    val player = act(Model(Some(game)), right)
    assert(player.game.get.hp == 10)
    assert(player.flashes.keySet == Set(Target.Monster(0)))
    assert(player.playback.get._2 == 200)
    assert(act(player, Action.Wait) == player)
    val first = advance(player)
    assert(first.game.get.hp == 9 && first.flashes.keySet == Set(Target.Player))
    val old = first.flashes(Target.Player).serial
    val second = advance(first)
    assert(second.game.get.hp == 8)
    assert(second.flashes(Target.Player).serial > old)
    assert(update(ClearFlash(Target.Player, old), second)._1 == second)
    assert(update(Advance(first.playback.get._1), second)._1 == second)
    assert(second.pending.isEmpty && second.playback.nonEmpty)
    val done = advance(second)
    assert(done.playback.isEmpty && done.flashes.isEmpty)
    assert(Sub.toMap(subscriptions(done)).isEmpty)
  }

  test("defeated ghosts disappear before movement and never override occupants") {
    val game = base.copy(enemies = Vector(Enemy(0, Pos(2, 1), 2)))
    val killed = act(Model(Some(game)), right)
    assert(killed.game.get.enemies.isEmpty && killed.pending.isEmpty)
    assert(flashAt(killed, killed.game.get, Pos(2, 1)).exists(_.damage.defeated))
    val moved = act(advance(killed), right)
    assert(moved.game.get.player == Pos(2, 1))
    assert(moved.playback.get._2 == 60)
    assert(flashAt(moved, moved.game.get, Pos(2, 1)).isEmpty)
    val occupied = killed.game.get.copy(enemies = Vector(Enemy(1, Pos(2, 1))))
    assert(flashAt(killed, occupied, Pos(2, 1)).isEmpty)
  }

  test("restart cancels playback and effects without reusing timer serials") {
    val hit = act(Model(Some(base.copy(enemies = Vector(Enemy(0, Pos(2, 1)))))), right)
    val restarted = update(Restart, hit)._1
    assert(restarted.game.isEmpty && restarted.flashes.isEmpty && restarted.pending.isEmpty && restarted.playback.isEmpty)
    val ready = update(Ready(42), restarted)._1
    val again = act(ready.copy(game = hit.game), right)
    assert(again.effectSerial > hit.effectSerial && again.playbackSerial > hit.playbackSerial)
    assert(update(Advance(hit.playback.get._1), again)._1 == again)
    assert(update(ClearFlash(Target.Monster(0), hit.effectSerial), again)._1 == again)
  }
}
