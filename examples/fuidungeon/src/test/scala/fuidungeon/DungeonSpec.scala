package fuidungeon

import scala.util.Random
import org.scalatest.funsuite.AnyFunSuite
import Game.*

class DungeonSpec extends AnyFunSuite {
  // Independent flood fill checks the generated graph without using the generator's BFS.
  private def distances(board: Board): Map[Pos, Int] = {
    var found = Map(board.start -> 0)
    var frontier = Set(board.start)
    var depth = 0
    while (frontier.nonEmpty) {
      depth += 1
      frontier = frontier.flatMap(p => Direction.values.map(p + _))
        .filter(p => board.isFloor(p) && !found.contains(p))
      found ++= frontier.map(_ -> depth)
    }
    found
  }

  test("200 seeds and Long boundaries produce connected reproducible variable-size expeditions") {
    val seeds = (0L until 200L).toVector ++ Vector(Long.MinValue, Long.MaxValue, -1L)
    val games = seeds.map { seed =>
      val game = initial(seed)
      val generated = Dungeon.generate(new Random(seed))
      val board = game.board
      val n = generated.roomCount
      assert(game == initial(seed), s"seed $seed")
      assert(board == generated.board && n >= 6 && n <= 10)
      assert(board.width <= 64 && board.height <= 64)
      assert(board.rows.forall(_.length == board.width))
      assert(board.rows.head.forall(_ == '#') && board.rows.last.forall(_ == '#'))
      assert(board.rows.forall(row => row.head == '#' && row.last == '#'))
      assert(board.rows.mkString.count(_ == 'S') == 1 && board.rows.mkString.count(_ == 'E') == 1)
      assert(board.start != board.exit && game.player == board.start)
      val reachable = distances(board)
      assert(reachable.keySet == board.floors.toSet)
      val farthest = board.floors.sortBy(p => (-reachable(p), p.y, p.x)).head
      assert(board.exit == farthest)
      assert(game.hp == 10 && game.turns == 0 && !game.hasKey && game.status == Status.Playing)
      assert(game.enemies.size == n - 3 && game.enemies.forall(_.hp == 4))
      assert(game.items.values.count(_ == Item.Key) == 1)
      assert(game.items.values.count(_ == Item.Potion) == (n + 1) / 2)
      val occupied = game.enemies.map(_.position) ++ game.items.keys
      assert(occupied.distinct.size == occupied.size)
      assert(occupied.forall(p => board.isFloor(p) && p != board.start && p != board.exit))
      assert(game.enemies.forall(e => reachable(e.position) >= 4))
      game
    }
    assert(games.map(g => (g.board.width, g.board.height)).distinct.size > 1)
    assert(games.map(_.board.rows).distinct.size > 1)
  }

  test("normal growth preserves room sizes, separation, bounds and a connected parent tree") {
    for (seed <- 0L until 30L) {
      val layout = Dungeon.grow(new Random(seed), 10)
      assert(layout.rooms.size >= 6 && layout.rooms.size <= 10)
      assert(layout.edges.size == layout.rooms.size - 1)
      layout.rooms.zipWithIndex.foreach { (room, i) =>
        assert(room.width >= 4 && room.width <= 7 && room.height >= 4 && room.height <= 7)
        assert(layout.rooms.take(i).forall(_.separated(room)))
        if (i > 0) assert(layout.edges.exists { (a, b) => a < i && b == i })
      }
      assert(layout.rooms.map(_.right).max - layout.rooms.map(_.x).min + 1 <= 62)
      assert(layout.rooms.map(_.bottom).max - layout.rooms.map(_.y).min + 1 <= 62)
    }
  }

  test("exhaustion before six rooms uses the bounded six-room fallback") {
    var calls = 0
    val layout = Dungeon.grow(new Random(42), 10, (_, parent) => { calls += 1; parent })
    assert(calls == 128)
    assert(layout.rooms.size == 6 && layout.edges.size == 5)
    assert(layout.rooms.sliding(2).forall(pair => pair(0).right < pair(1).x))
    assert(layout.rooms.last.right - layout.rooms.head.x + 1 <= 62)
    assert(layout == Dungeon.grow(new Random(42), 10, (_, parent) => parent))
  }

  test("exhaustion after six rooms keeps the accepted rooms") {
    var calls = 0
    val layout = Dungeon.grow(new Random(42), 10, (_, parent) => {
      calls += 1
      if (calls <= 5) Dungeon.Room(calls * 9, 0, 4, 4) else parent
    })
    assert(calls == 5 + 128)
    assert(layout.rooms.size == 6 && layout.rooms.last.x == 45)
    assert(layout.edges.size == 5)
  }

  test("fallback rooms have valid dimensions, gaps and total extent") {
    for (seed <- 0L until 30L) {
      val rooms = Dungeon.fallback(new Random(seed)).rooms
      assert(rooms.size == 6)
      assert(rooms.forall(r => r.width >= 4 && r.width <= 7 && r.height >= 4 && r.height <= 7))
      rooms.sliding(2).foreach { pair =>
        val gap = pair(1).x - pair(0).right - 1
        assert(gap >= 1 && gap <= 3)
      }
      assert(rooms.last.right - rooms.head.x + 1 <= 62)
    }
  }
}
