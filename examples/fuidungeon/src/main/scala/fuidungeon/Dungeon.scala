package fuidungeon

import scala.util.Random
import Game.*

/** Bounded generation; all mutable working data and randomness stay inside initialization. */
private[fuidungeon] object Dungeon {
  case class Room(x: Int, y: Int, width: Int, height: Int) {
    def right: Int = x + width - 1
    def bottom: Int = y + height - 1
    def center: Pos = Pos(x + width / 2, y + height / 2)
    def cells: Vector[Pos] = (for {
      yy <- y to bottom
      xx <- x to right
    } yield Pos(xx, yy)).toVector
    def separated(other: Room): Boolean =
      right + 1 < other.x || other.right + 1 < x || bottom + 1 < other.y || other.bottom + 1 < y
  }
  case class Layout(rooms: Vector[Room], edges: Vector[(Int, Int)])
  case class Generated(board: Board, roomCount: Int)

  private def candidate(random: Random, parent: Room): Room = {
    val width = 4 + random.nextInt(4)
    val height = 4 + random.nextInt(4)
    val gap = 1 + random.nextInt(3)
    random.nextInt(4) match {
      case 0 => Room(parent.center.x - width / 2, parent.y - gap - height, width, height)
      case 1 => Room(parent.right + gap + 1, parent.center.y - height / 2, width, height)
      case 2 => Room(parent.center.x - width / 2, parent.bottom + gap + 1, width, height)
      case _ => Room(parent.x - gap - width, parent.center.y - height / 2, width, height)
    }
  }

  private def fits(rooms: Vector[Room], room: Room): Boolean = {
    val all = rooms :+ room
    rooms.forall(_.separated(room)) &&
      all.map(_.right).max - all.map(_.x).min + 1 <= 62 &&
      all.map(_.bottom).max - all.map(_.y).min + 1 <= 62
  }

  /** The injectable candidate source also lets tests exercise exhaustion deterministically. */
  private[fuidungeon] def grow(
      random: Random,
      target: Int,
      propose: (Random, Room) => Room = candidate,
      attemptsPerRoom: Int = 128
  ): Layout = {
    var rooms = Vector(Room(0, 0, 4 + random.nextInt(4), 4 + random.nextInt(4)))
    var edges = Vector.empty[(Int, Int)]
    var stopped = false
    while (rooms.size < target && !stopped) {
      var accepted = false
      var attempts = 0
      while (attempts < attemptsPerRoom && !accepted) {
        val parent = random.nextInt(rooms.size)
        val next = propose(random, rooms(parent))
        if (fits(rooms, next)) {
          edges :+= (parent, rooms.size)
          rooms :+= next
          accepted = true
        }
        attempts += 1
      }
      stopped = !accepted
    }
    if (rooms.size >= 6) Layout(rooms, edges) else fallback(random)
  }

  private[fuidungeon] def fallback(random: Random): Layout = {
    var x = 0
    val rooms = Vector.fill(6) {
      val room = Room(x, 0, 4 + random.nextInt(4), 4 + random.nextInt(4))
      x += room.width + 1 + random.nextInt(3)
      room
    }
    Layout(rooms, (0 until 5).map(i => (i, i + 1)).toVector)
  }

  def generate(random: Random): Generated = {
    val layout = grow(random, 6 + random.nextInt(5))
    val rooms = layout.rooms
    val additional = (for {
      a <- rooms.indices
      b <- (a + 1) until rooms.size
      if !layout.edges.contains((a, b))
    } yield (a, b)).sortBy { (a, b) =>
      val p = rooms(a).center
      val q = rooms(b).center
      (math.abs(p.x - q.x) + math.abs(p.y - q.y), a, b)
    }.take(2)
    var floors = rooms.flatMap(_.cells).toSet
    for ((a, b) <- layout.edges ++ additional) {
      val from = rooms(a).center
      val to = rooms(b).center
      val corner = if (random.nextBoolean()) Pos(to.x, from.y) else Pos(from.x, to.y)
      def segment(p: Pos, q: Pos): Set[Pos] = (for {
        y <- p.y.min(q.y) to p.y.max(q.y)
        x <- p.x.min(q.x) to p.x.max(q.x)
      } yield Pos(x, y)).toSet
      floors ++= segment(from, corner) ++ segment(corner, to)
    }
    val minX = floors.map(_.x).min - 1
    val minY = floors.map(_.y).min - 1
    val width = floors.map(_.x).max - minX + 2
    val height = floors.map(_.y).max - minY + 2
    val shifted = floors.map(p => Pos(p.x - minX, p.y - minY))
    val start = Pos(rooms.head.center.x - minX, rooms.head.center.y - minY)
    val distances = distancesFrom(shifted, start)
    val exit = shifted.toVector.sortBy(p => (-distances(p), p.y, p.x)).head
    val rows = Vector.tabulate(height, width) { (y, x) =>
      val p = Pos(x, y)
      if (p == start) 'S' else if (p == exit) 'E' else if (shifted(p)) '.' else '#'
    }.map(_.mkString)
    Generated(Board(rows), rooms.size)
  }

  def distancesFrom(floors: Set[Pos], start: Pos): Map[Pos, Int] = {
    val queue = scala.collection.mutable.Queue(start)
    val distances = scala.collection.mutable.Map(start -> 0)
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      Direction.values.foreach { direction =>
        val next = current + direction
        if (floors(next) && !distances.contains(next)) {
          distances(next) = distances(current) + 1
          queue.enqueue(next)
        }
      }
    }
    distances.toMap
  }
}
