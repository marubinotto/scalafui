package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*

class EnemySpec extends AnyFunSuite {
  private val board = Board(Vector("############", "#S........E#", "#..........#", "############"))
  private val base = State(board, board.start)
  private def enemy(kind: EnemyKind, p: Pos, id: Int = 0): Enemy = Enemy.spawn(id, p, kind)

  test("species start with their own HP and floor weights interpolate and cap") {
    assert(EnemyKind.values.map(k => enemy(k, Pos(2, 1)).hp).toVector == Vector(4, 4, 8))
    val expected = Map(1 -> Vector(100.0, 0.0, 0.0), 2 -> Vector(85.0, 15.0, 0.0),
      3 -> Vector(70.0, 30.0, 0.0), 4 -> Vector(57.5, 35.0, 7.5), 5 -> Vector(45.0, 40.0, 15.0),
      6 -> Vector(39.0, 41.0, 20.0), 10 -> Vector(15.0, 45.0, 40.0), 100 -> Vector(15.0, 45.0, 40.0))
    expected.foreach((floor, weights) => assert(enemyWeights(floor) == weights))
    val kinds = (0L until 50L).flatMap(seed => initialFloor(seed, 10).enemies.map(_.kind)).toSet
    assert(kinds == EnemyKind.values.toSet)
    for (seed <- 0L until 30L) {
      assert(initial(seed).enemies.forall(_.kind == EnemyKind.Goblin))
      assert(initialFloor(seed, 10) == initialFloor(seed, 10))
      assert(initialFloor(seed, 10).enemies.forall(e => e.hp == e.kind.initialHp))
      assert(initial(seed).enemies.map(_.position) == initialFloor(seed, 10).enemies.map(_.position))
    }
  }

  test("bat moves twice even far away, stays on free floors, and can move then attack") {
    val outcomes = (0L until 100L).map { seed =>
      val game = base.copy(randomSeed = seed, enemies = Vector(enemy(EnemyKind.CaveBat, Pos(8, 1))))
      val next = step(game, Action.Wait)
      assert(next == step(game, Action.Wait))
      assert(board.isFloor(next.enemies.head.position) && next.enemies.head.position != next.player)
      assert(next.hp == 10)
      next.enemies.head.position
    }
    assert(outcomes.contains(Pos(6, 1))) // Two steps, outside pursuit range.
    val corridor = Board(Vector("######", "#S..E#", "######"))
    val approaching = State(corridor, corridor.start, enemies = Vector(enemy(EnemyKind.CaveBat, Pos(3, 1))))
    val attacks = (0L until 30L).map(seed => stepWithEvents(approaching.copy(randomSeed = seed * 6364136223846793005L), Action.Wait))
    assert(attacks.exists(t => t.state.enemies.head.position == Pos(2, 1) && t.state.hp == 9 && t.damage.size == 1))
    assert(attacks.exists(_.state.hp == 10))
  }

  test("trapped bats wait and other enemies block their movement") {
    val trapped = Board(Vector("########", "#S.E####", "#####.##", "########"))
    val game = State(trapped, trapped.start, enemies = Vector(enemy(EnemyKind.CaveBat, Pos(5, 2))))
    assert(step(game, Action.Wait).enemies == game.enemies)
    for (seed <- 0L until 30L) {
      val crowded = base.copy(randomSeed = seed, enemies = Vector(
        enemy(EnemyKind.CaveBat, Pos(8, 1)), enemy(EnemyKind.Goblin, Pos(7, 1), 1),
        enemy(EnemyKind.Goblin, Pos(9, 1), 2)))
      val next = step(crowded, Action.Wait)
      assert(next.enemies.map(_.position).distinct.size == next.enemies.size)
      assert(next.enemies.head.position != Pos(7, 1) && next.enemies.head.position != Pos(9, 1))
    }
  }

  test("bat attacks twice consecutively in ID order and death stops all remaining actions") {
    val game = base.copy(enemies = Vector(enemy(EnemyKind.Goblin, Pos(1, 2), 1), enemy(EnemyKind.CaveBat, Pos(2, 1))))
    val next = stepWithEvents(game, Action.Wait)
    assert(next.state.hp == 7 && next.damage.size == 3)
    assert(next.state.log.takeRight(3) == Vector("Cave Bat hits you for 1.", "Cave Bat hits you for 1.", "Goblin hits you for 1."))
    val dead = stepWithEvents(game.copy(hp = 1), Action.Wait)
    assert(dead.state.hp == 0 && dead.state.status == Status.Lost && dead.damage.size == 1)
    assert(dead.damage.head.defeated)
  }

  test("ogre damage covers 1 to 3, agrees with logs and events, and follows goblin pursuit") {
    val amounts = (0L until 100L).map { seed =>
      val game = base.copy(randomSeed = seed, enemies = Vector(enemy(EnemyKind.Ogre, Pos(2, 1))))
      val next = stepWithEvents(game, Action.Wait)
      val amount = game.hp - next.state.hp
      assert(amount >= 1 && amount <= 3)
      assert(next.state.log.last == s"Ogre hits you for $amount.")
      assert(next.damage == Vector(Damage(Target.Player, game.player, false)))
      amount
    }.toSet
    assert(amounts == Set(1, 2, 3))
    val pursuing = step(base.copy(enemies = Vector(enemy(EnemyKind.Ogre, Pos(4, 1)))), Action.Wait)
    assert(pursuing.enemies.head.position == Pos(3, 1) && pursuing.hp == 10)
  }

  test("turn RNG advances only on valid turns and descent uses destination floor weights") {
    val game = base.copy(randomSeed = 91, enemies = Vector(enemy(EnemyKind.Ogre, Pos(2, 1))))
    val first = step(game, Action.Wait)
    assert(first.randomSeed != game.randomSeed)
    assert(step(first, Action.Wait) == step(step(game, Action.Wait), Action.Wait))
    assert(step(game, Action.Move(Direction.Up)).randomSeed == game.randomSeed)
    assert(step(game.copy(status = Status.Lost), Action.Wait).randomSeed == game.randomSeed)
    val pending = game.copy(floor = 9, status = Status.Descending)
    val next = nextFloor(pending, 77)
    val generated = initialFloor(77, 10)
    assert(next.enemies == generated.enemies && next.randomSeed == generated.randomSeed)
    assert(next.floor == 10 && next.hp == pending.hp)
  }

  test("each species preserves its identity in damage and defeated afterimages") {
    for (kind <- EnemyKind.values) {
      val game = base.copy(enemies = Vector(enemy(kind, Pos(2, 1)).copy(hp = 2)))
      val next = stepWithEvents(game, Action.Move(Direction.Right))
      assert(next.state.enemies.isEmpty && next.state.hp == 10)
      assert(next.damage == Vector(Damage(Target.Monster(0), Pos(2, 1), true, Some(kind))))
      val model = Main.update(Main.Act(Action.Move(Direction.Right)), Main.Model(Some(game)))._1
      assert(Main.flashAt(model, model.game.get, Pos(2, 1)).flatMap(_.damage.kind).contains(kind))
      val cleared = Main.update(Main.ClearFlash(Target.Monster(0), model.effectSerial), model)._1
      assert(cleared.game.get.randomSeed == model.game.get.randomSeed)
    }
  }
}
