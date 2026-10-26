package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*
import Main.*

class MainSpec extends AnyFunSuite {
  test("initialization locks actions and restart until the seed arrives") {
    val loading = Model()
    assert(update(Act(Action.Wait), loading)._1 == loading)
    assert(update(Restart, loading)._1 == loading)
    val ready = update(Ready(42), loading)._1
    assert(ready.game.contains(initial(42)))
    assert(update(Ready(99), ready)._1 == ready)
  }

  test("restart clears previous progress and a new seed creates a fresh expedition") {
    val old = initial(1).copy(hp = 0, hasKey = true, turns = 12, status = Status.Lost)
    val loading = update(Restart, Model(Some(old)))._1
    assert(loading.game.isEmpty)
    val restarted = update(Ready(2), loading)._1.game.get
    assert(restarted == initial(2))
    assert(restarted.items != old.items)
    assert(restarted.board != old.board)
  }

  test("keyboard mappings match direction and wait actions") {
    for (key <- Seq("ArrowUp", "w", "W")) assert(actionForKey(key).contains(Action.Move(Direction.Up)))
    for (key <- Seq("ArrowRight", "d", "D")) assert(actionForKey(key).contains(Action.Move(Direction.Right)))
    for (key <- Seq("ArrowDown", "s", "S")) assert(actionForKey(key).contains(Action.Move(Direction.Down)))
    for (key <- Seq("ArrowLeft", "a", "A")) assert(actionForKey(key).contains(Action.Move(Direction.Left)))
    assert(actionForKey(" ").contains(Action.Wait))
    assert(actionForKey("Tab").isEmpty)
  }
}
