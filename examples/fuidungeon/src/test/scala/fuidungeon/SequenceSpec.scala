package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*

class SequenceSpec extends AnyFunSuite {
  private val board = Board(Vector("#########", "#S.....E#", "#########"))
  private val base = State(board, board.start)

  test("bat movement and attack are separate; wrappers preserve snapshots and RNG") {
    val game = base.copy(enemies = Vector(Enemy.spawn(4, Pos(3, 1), EnemyKind.CaveBat)))
    val frames = turnSequence(game, Action.Wait)
    assert(frames.size == 3 && frames.head.kind == ActionKind.Wait)
    assert(frames.head.state.hp == 10)
    assert(frames(1).kind == ActionKind.Move)
    assert(frames.forall(_.state.turns == 1))
    val result = stepWithEvents(game, Action.Wait)
    assert(result.state == frames.last.state && result.damage == frames.flatMap(_.damage))
    assert(frames == turnSequence(game, Action.Wait))
    val random = new scala.util.Random(game.randomSeed)
    random.nextInt(2)
    if (frames.last.kind == ActionKind.Move) random.nextInt(2)
    assert(frames.last.state.randomSeed == random.nextLong())
  }

  test("ID order, consecutive bat attacks, death truncation, and idle enemies") {
    val game = base.copy(player = Pos(3, 1), enemies = Vector(
      Enemy(8, Pos(4, 1)), Enemy.spawn(2, Pos(2, 1), EnemyKind.CaveBat)))
    val frames = turnSequence(game, Action.Wait)
    assert(frames.map(_.state.hp) == Vector(10, 9, 8, 7))
    assert(frames(1).state.log.last.startsWith("Cave Bat"))
    assert(frames(2).state.log.last.startsWith("Cave Bat"))
    assert(frames(3).state.log.last.startsWith("Goblin"))
    assert(turnSequence(game.copy(hp = 1), Action.Wait).size == 2)
    assert(turnSequence(base.copy(enemies = Vector(Enemy(0, Pos(7, 1)))), Action.Wait).size == 1)
  }

  test("invalid actions do not replay; kills and descent omit enemy actions") {
    assert(turnSequence(base, Action.Move(Direction.Up)).isEmpty)
    assert(turnSequence(base.copy(status = Status.Lost), Action.Wait).isEmpty)
    val kill = turnSequence(base.copy(enemies = Vector(Enemy(0, Pos(2, 1), 2))), Action.Move(Direction.Right))
    assert(kill.size == 1 && kill.head.damage.head.defeated)
    val exit = turnSequence(base.copy(player = Pos(6, 1), hasKey = true,
      enemies = Vector(Enemy(0, Pos(5, 1)))), Action.Move(Direction.Right))
    assert(exit.size == 1 && exit.head.state.status == Status.Descending && exit.head.state.hp == 10)
  }
}
