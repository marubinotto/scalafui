package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*

class GameSpec extends AnyFunSuite {
  private val board = Board(Vector(
    "#########",
    "#S.....E#",
    "#.......#",
    "#.......#",
    "#.......#",
    "#########"
  ))
  private def state = State(board, board.start)
  private def move(direction: Direction) = Action.Move(direction)

  test("walls do not spend a turn; movement and waiting do") {
    assert(step(state, move(Direction.Up)) == state)
    val moved = step(state, move(Direction.Right))
    assert(moved.player == Pos(2, 1) && moved.turns == 1)
    val waited = step(moved, Action.Wait)
    assert(waited.player == moved.player && waited.turns == 2)
  }

  test("bumping attacks without moving; a killed enemy cannot retaliate") {
    val game = state.copy(enemies = Vector(Enemy(0, Pos(2, 1))))
    val hit = step(game, move(Direction.Right))
    assert(hit.player == game.player && hit.enemies.head.hp == 2 && hit.hp == 9)
    val killed = step(hit, move(Direction.Right))
    assert(killed.enemies.isEmpty && killed.hp == 9 && killed.player == game.player)
    assert(killed.turns == 2)
  }

  test("key and potion are consumed on entry and healing is capped") {
    val game = state.copy(hp = 8, items = Map(Pos(2, 1) -> Item.Key, Pos(3, 1) -> Item.Potion))
    val key = step(game, move(Direction.Right))
    assert(key.hasKey && !key.items.contains(Pos(2, 1)))
    val healed = step(key, move(Direction.Right))
    assert(healed.hp == 10 && healed.items.isEmpty)
    val low = step(state.copy(hp = 1, items = Map(Pos(2, 1) -> Item.Potion)), move(Direction.Right))
    assert(low.hp == 5)
    val full = step(state.copy(items = Map(Pos(2, 1) -> Item.Potion)), move(Direction.Right))
    assert(full.hp == 10 && full.items.isEmpty)
  }

  test("exit without key is floor; winning suppresses enemy attacks") {
    val game = state.copy(player = Pos(6, 1), enemies = Vector(Enemy(0, Pos(7, 2))))
    val locked = step(game, move(Direction.Right))
    assert(locked.status == Status.Playing && locked.player == board.exit && locked.hp == 9)
    val won = step(game.copy(hasKey = true), move(Direction.Right))
    assert(won.status == Status.Won && won.hp == 10 && won.turns == 1)
    assert(step(won, Action.Wait) == won)
  }

  test("enemies pursue within five steps with deterministic ties but do not attack after moving") {
    val near = step(state.copy(enemies = Vector(Enemy(0, Pos(4, 3)))), Action.Wait)
    assert(near.enemies.head.position == Pos(4, 2) && near.hp == 10)
    val far = state.copy(enemies = Vector(Enemy(0, Pos(5, 3))))
    assert(step(far, Action.Wait).enemies == far.enemies)
    val approaches = step(state.copy(enemies = Vector(Enemy(0, Pos(3, 1)))), Action.Wait)
    assert(approaches.enemies.head.position == Pos(2, 1) && approaches.hp == 10)
  }

  test("pathfinding goes around walls and occupied tiles") {
    val maze = Board(Vector("#######", "#S.#.E#", "#.....#", "#######"))
    val route = path(maze, maze.start, maze.exit).get
    assert(route.size == 6 && route.last == maze.exit)
    assert(route.forall(maze.isFloor))
    assert(path(maze, maze.start, maze.exit, Set(Pos(2, 2))).isEmpty)
    assert(path(board, Pos(3, 1), Pos(1, 1), Set(Pos(2, 1))).exists(_.head == Pos(3, 2)))
  }

  test("enemies act by ID and use updated occupancy without overlapping") {
    val game = state.copy(enemies = Vector(Enemy(1, Pos(4, 1)), Enemy(0, Pos(3, 1))))
    val after = step(game, Action.Wait)
    assert(after.enemies.find(_.id == 0).get.position == Pos(2, 1))
    assert(after.enemies.find(_.id == 1).get.position == Pos(4, 2))
    assert(after.enemies.map(_.position).distinct.size == after.enemies.size)
  }

  test("death stops the remaining enemies and all subsequent actions") {
    val game = state.copy(hp = 1, enemies = Vector(Enemy(0, Pos(2, 1)), Enemy(1, Pos(3, 2))))
    val lost = step(game, Action.Wait)
    assert(lost.hp == 0 && lost.status == Status.Lost)
    assert(lost.enemies(1) == game.enemies(1))
    assert(step(lost, move(Direction.Down)) == lost)
    assert(lost.log.count(_ == "A goblin hits you for 1.") == 1)
  }

  test("the journal retains only its six most recent messages") {
    val after = (1 to 12).foldLeft(state)((game, _) => step(game, Action.Wait))
    assert(after.log == Vector.fill(6)("You wait."))
  }
}
