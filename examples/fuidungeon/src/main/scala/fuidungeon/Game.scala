package fuidungeon

import scala.collection.immutable.Queue
import scala.util.Random

/** All game rules are independent of the browser. Randomness enters only as a seed. */
object Game {
  case class Pos(x: Int, y: Int) {
    def +(direction: Direction): Pos = Pos(x + direction.dx, y + direction.dy)
  }
  enum Direction(val dx: Int, val dy: Int) {
    case Up extends Direction(0, -1)
    case Right extends Direction(1, 0)
    case Down extends Direction(0, 1)
    case Left extends Direction(-1, 0)
  }
  enum Action {
    case Move(direction: Direction)
    case Wait
  }
  enum Status { case Playing, Descending, Lost }
  enum Item { case Key, Potion }
  enum Target {
    case Player
    case Goblin(id: Int)
  }
  case class Damage(target: Target, position: Pos, defeated: Boolean)
  case class TurnResult(state: State, damage: Vector[Damage] = Vector.empty)
  case class Enemy(id: Int, position: Pos, hp: Int = 4)
  case class Board(rows: Vector[String]) {
    val width: Int = rows.head.length
    val height: Int = rows.length
    def isFloor(p: Pos): Boolean =
      p.y >= 0 && p.y < height && p.x >= 0 && p.x < width && rows(p.y)(p.x) != '#'
    val floors: Vector[Pos] = (for {
      y <- rows.indices
      x <- rows(y).indices
      p = Pos(x, y) if isFloor(p)
    } yield p).toVector
    val start: Pos = floors.find(p => rows(p.y)(p.x) == 'S').get
    val exit: Pos = floors.find(p => rows(p.y)(p.x) == 'E').get
  }
  val maxHp = 10
  case class State(
      board: Board,
      player: Pos,
      hp: Int = maxHp,
      floor: Int = 1,
      hasKey: Boolean = false,
      enemies: Vector[Enemy] = Vector.empty,
      items: Map[Pos, Item] = Map.empty,
      status: Status = Status.Playing,
      turns: Int = 0,
      log: Vector[String] = Vector("Find the key and reach the door to the next floor.")
  ) {
    def note(message: String): State = copy(log = (log :+ message).takeRight(6))
  }

  /** Breadth-first search; ties consistently prefer up, right, down, then left.
    * The returned path excludes the origin and includes the destination.
    */
  def path(board: Board, from: Pos, to: Pos, blocked: Set[Pos] = Set.empty): Option[Vector[Pos]] = {
    @annotation.tailrec
    def search(queue: Queue[(Pos, Vector[Pos])], seen: Set[Pos]): Option[Vector[Pos]] =
      queue.dequeueOption match {
        case None => None
        case Some(((position, steps), rest)) =>
          if (position == to) Some(steps)
          else {
            val next = Direction.values.toVector.map(position + _)
              .filter(p => board.isFloor(p) && !blocked(p) && !seen(p))
            search(rest.enqueueAll(next.map(p => (p, steps :+ p))), seen ++ next)
          }
      }
    search(Queue((from, Vector.empty)), Set(from))
  }

  def initial(seed: Long): State = {
    val random = new Random(seed)
    val generated = Dungeon.generate(random)
    val dungeon = generated.board
    val distances = Dungeon.distancesFrom(dungeon.floors.toSet, dungeon.start)
    val available = dungeon.floors.filter(p => p != dungeon.start && p != dungeon.exit)
    val enemyPositions = random.shuffle(available.filter(p =>
      distances(p) >= 4
    )).take(generated.roomCount - 3)
    val items = Vector(Item.Key) ++ Vector.fill((generated.roomCount + 1) / 2)(Item.Potion)
    val itemPositions = random.shuffle(available.filterNot(enemyPositions.contains)).take(items.size)
    State(dungeon, dungeon.start,
      enemies = enemyPositions.zipWithIndex.map((p, id) => Enemy(id, p)),
      items = itemPositions.zip(items).toMap)
  }

  /** Only a pending descent can replace the floor; expedition progress survives. */
  def nextFloor(state: State, seed: Long): State =
    if (state.status != Status.Descending) state
    else initial(seed).copy(
      floor = state.floor + 1,
      hp = state.hp,
      turns = state.turns,
      log = state.log
    ).note(s"You enter floor ${state.floor + 1}. Find its key and keep descending.")

  def step(state: State, action: Action): State = stepWithEvents(state, action).state

  def stepWithEvents(state: State, action: Action): TurnResult = {
    if (state.status != Status.Playing) TurnResult(state)
    else {
      val events = Vector.newBuilder[Damage]
      val afterPlayer = action match {
        case Action.Wait => state.note("You wait.")
        case Action.Move(direction) =>
          val target = state.player + direction
          if (!state.board.isFloor(target)) return TurnResult(state)
          state.enemies.find(_.position == target) match {
            case Some(enemy) =>
              events += Damage(Target.Goblin(enemy.id), target, enemy.hp <= 2)
              val remaining = state.enemies.map(e => if (e.id == enemy.id) e.copy(hp = e.hp - 2) else e)
                .filter(_.hp > 0)
              state.copy(enemies = remaining).note(
                if (enemy.hp <= 2) "You defeat a goblin." else "You hit a goblin for 2.")
            case None =>
              val moved = state.copy(player = target, items = state.items - target)
              state.items.get(target) match {
                case Some(Item.Key) => moved.copy(hasKey = true).note("You found the key!")
                case Some(Item.Potion) =>
                  moved.copy(hp = (state.hp + 4).min(maxHp)).note("You drink a potion.")
                case None => moved
              }
          }
      }
      val advanced = afterPlayer.copy(turns = state.turns + 1)
      if (advanced.hasKey && advanced.player == advanced.board.exit)
        TurnResult(advanced.copy(status = Status.Descending, hasKey = false).note("You unlock the door and descend."))
      else enemyTurn(TurnResult(advanced, events.result()))
    }
  }

  private def enemyTurn(turn: TurnResult): TurnResult =
    turn.state.enemies.sortBy(_.id).foldLeft(turn) { (result, enemy) =>
      val current = result.state
      if (current.status != Status.Playing) result
      else if (math.abs(enemy.position.x - current.player.x) + math.abs(enemy.position.y - current.player.y) == 1) {
        val hit = current.copy(hp = (current.hp - 1).max(0)).note("A goblin hits you for 1.")
        result.copy(
          state = if (hit.hp == 0) hit.copy(status = Status.Lost).note("You fell in the dungeon.") else hit,
          damage = result.damage :+ Damage(Target.Player, current.player, hit.hp == 0)
        )
      } else {
        val blocked = current.enemies.filterNot(_.id == enemy.id).map(_.position).toSet
        path(current.board, enemy.position, current.player, blocked) match {
          case Some(steps) if steps.nonEmpty && steps.size <= 5 =>
            result.copy(state = current.copy(enemies = current.enemies.map(e =>
              if (e.id == enemy.id) e.copy(position = steps.head) else e)))
          case _ => result
        }
      }
    }
}
