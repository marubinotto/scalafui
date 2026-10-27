package fuidungeon

import scala.scalajs.LinkingInfo
import scala.concurrent.duration.DurationInt
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalajs.dom.URL
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*
import fui.*
import Game.*

object Main {
  case class Flash(damage: Damage, serial: Long)
  case class Model(
      game: Option[State] = None,
      flashes: Map[Target, Flash] = Map.empty,
      effectSerial: Long = 0,
      pending: Vector[TurnFrame] = Vector.empty,
      playback: Option[(Long, Int)] = None,
      playbackSerial: Long = 0
  )
  enum Msg {
    case Ready(seed: Long)
    case FloorReady(seed: Long)
    case Act(action: Action)
    case Advance(serial: Long)
    case Restart
    case ClearFlash(target: Target, serial: Long)
  }

  export Msg.*

  private def newGame: Cmd[Msg] = IO(Ready(scala.util.Random.nextLong())).toCmd
  def init(@annotation.unused url: URL): (Model, Cmd[Msg]) = (Model(), newGame)
  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) = msg match {
    case Ready(seed) if model.game.isEmpty => (model.copy(game = Some(initial(seed)), flashes = Map.empty), Cmd.none)
    case FloorReady(seed) if model.game.exists(_.status == Status.Descending) =>
      (model.copy(game = model.game.map(Game.nextFloor(_, seed)), flashes = Map.empty, pending = Vector.empty, playback = None), Cmd.none)
    case Act(action) if model.playback.isEmpty =>
      val frames = model.game.toVector.flatMap(Game.turnSequence(_, action))
      frames.headOption match {
        case None => (model, Cmd.none)
        case Some(frame) => showFrame(model, frame, frames.tail)
      }
    case Advance(serial) if model.playback.exists(_._1 == serial) =>
      model.pending.headOption match {
        case Some(frame) => showFrame(model, frame, model.pending.tail)
        case None => (model.copy(playback = None, flashes = Map.empty), Cmd.none)
      }
    case Restart if model.game.exists(_.status != Status.Descending) =>
      (model.copy(game = None, flashes = Map.empty, pending = Vector.empty, playback = None), newGame)
    case ClearFlash(target, serial) if model.flashes.get(target).exists(_.serial == serial) =>
      (model.copy(flashes = model.flashes - target), Cmd.none)
    case _ => (model, Cmd.none)
  }

  def frameDelay(kind: ActionKind): Int = if (kind == ActionKind.Attack) 200 else 60

  private def showFrame(model: Model, frame: TurnFrame, rest: Vector[TurnFrame]): (Model, Cmd[Msg]) = {
    val serial = model.playbackSerial + 1
    val shown = frame.damage.foldLeft(model.copy(game = Some(frame.state), flashes = Map.empty,
      pending = rest, playback = Some((serial, frameDelay(frame.kind))), playbackSerial = serial)) { (m, damage) =>
      val effect = m.effectSerial + 1
      m.copy(flashes = m.flashes.updated(damage.target, Flash(damage, effect)), effectSerial = effect)
    }
    if (frame.state.status == Status.Descending)
      (shown.copy(pending = Vector.empty, playback = None, flashes = Map.empty),
        IO(FloorReady(scala.util.Random.nextLong())).toCmd)
    else (shown, Cmd.none)
  }

  def subscriptions(model: Model): Sub[Msg] = {
    val playback = model.playback.fold[Sub[Msg]](Sub.Empty) { (serial, delay) =>
      Sub.timeout(delay.millis, Advance(serial), s"turn-$serial")
    }
    model.flashes.toVector.sortBy(_._2.serial).foldLeft(playback) { case (sub, (target, flash)) =>
      sub.combine(Sub.timeout(200.millis, ClearFlash(target, flash.serial), s"hit-${flash.serial}"))
    }
  }

  /** Living occupants take precedence over defeated-enemy afterimages. */
  def flashAt(model: Model, game: State, position: Pos): Option[Flash] =
    if (game.player == position) model.flashes.get(Target.Player)
    else game.enemies.find(_.position == position) match {
      case Some(enemy) => model.flashes.get(Target.Monster(enemy.id))
      case None => model.flashes.values.find(f => f.damage.target != Target.Player &&
        f.damage.defeated && f.damage.position == position)
    }


  def actionForKey(key: String): Option[Action] = key.toLowerCase match {
    case "arrowup" | "w" => Some(Action.Move(Direction.Up))
    case "arrowright" | "d" => Some(Action.Move(Direction.Right))
    case "arrowdown" | "s" => Some(Action.Move(Direction.Down))
    case "arrowleft" | "a" => Some(Action.Move(Direction.Left))
    case " " => Some(Action.Wait)
    case _ => None
  }

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    div(className := "shell")(
      header(className := "masthead")(
        p(className := "eyebrow")("SCALAFUI / PLAYABLE EXAMPLE"),
        h1("fui", span("dungeon")),
        p(className := "intro")("Find the key. Descend deeper. Stay alive.")
      ),
      model.game.fold[ReactElement](div(className := "loading", role := "status")("Preparing the dungeon…")) { game =>
        div(className := "game-layout")(
          viewBoard(model, game, dispatch),
          viewSidebar(model, game, dispatch)
        )
      },
      footer("Built with Scalafui · Scala.js · One action, one turn")
    )

  case class TileAppearance(icon: String, cssClass: String, label: String)

  def tileAppearance(game: State, position: Pos, flash: Option[Flash]): TileAppearance =
    if (position == game.player) TileAppearance("🤠", "player", "You")
    else game.enemies.find(_.position == position) match {
      case Some(enemy) => TileAppearance(enemy.kind.icon, "enemy", enemy.kind.label)
      case None =>
        flash.filter(_.damage.defeated).flatMap(_.damage.kind) match {
          case Some(kind) => TileAppearance(kind.icon, "enemy", s"Defeated ${kind.label}")
          case None => game.items.get(position) match {
            case Some(Item.Key) => TileAppearance("🔑", "key", "Key")
            case Some(Item.Potion) => TileAppearance("🧪", "potion", "Healing potion")
            case None if position == game.board.exit => TileAppearance("🚪", "exit", "Next floor")
            case None if !game.board.isFloor(position) => TileAppearance("#", "wall", "Wall")
            case _ => TileAppearance("·", "floor", "Floor")
          }
        }
    }

  private def viewBoard(model: Model, game: State, dispatch: Msg => Unit): ReactElement = {
    val wide = Camera.offset(game.board, game.player, 15)
    val narrow = Camera.offset(game.board, game.player, 9)
    val mapStyle = scala.scalajs.js.Dynamic.literal(
      "--map-width" -> s"${game.board.width * Camera.tileSize}px",
      "--map-height" -> s"${game.board.height * Camera.tileSize}px",
      "--wide-x" -> s"${wide.x}px", "--wide-y" -> s"${wide.y}px",
      "--narrow-x" -> s"${narrow.x}px", "--narrow-y" -> s"${narrow.y}px"
    )
    section(className := "dungeon-panel")(
      div(className := "panel-heading")(
        span(className := "floor-number", aria-"live" := "polite")(f"FLOOR ${game.floor}%02d"),
        span(className := "total-turns")(s"TOTAL TURNS ${game.turns}")
      ),
      div(className := "board", style := mapStyle, tabIndex := 0, role := "group",
        aria-"label" := "Dungeon. Use arrow keys or WASD to move, Space to wait.",
        onKeyDown := (event => {
          actionForKey(event.key).foreach { action =>
            event.preventDefault()
            if (!event.repeat) dispatch(Act(action))
          }
        }))(
        div(className := "map")(
          (for {
            y <- 0 until game.board.height
            x <- 0 until game.board.width
            position = Pos(x, y)
            flash = flashAt(model, game, position)
            appearance = tileAppearance(game, position, flash)
          } yield div(key := s"$x-$y", className := s"tile ${appearance.cssClass}${if (flash.nonEmpty) " hit" else ""}",
            title := s"${appearance.label} ($x, $y)")(appearance.icon))
        )
      ),
      p(className := "hint")("Click the map or Tab to focus · Arrow keys / WASD · Space to wait"),
      div(className := "legend")(
        span("🤠 You"), span("🔑 Key"), span("🧪 Potion"), span("🚪 Next floor")
      )
    )
  }

  private def viewSidebar(model: Model, game: State, dispatch: Msg => Unit): ReactElement = {
    val playing = game.status == Status.Playing && model.playback.isEmpty
    def control(label: String, action: Action, css: String): ReactElement =
      button(className := s"control $css", disabled := !playing,
        onClick := (_ => dispatch(Act(action))))(label)
    aside(className := "sidebar")(
      section(className := "card")(
        h2("Your expedition"),
        div(className := "stat")(span("HEALTH"), strong(s"${game.hp} / $maxHp")),
        div(className := "health-track", aria-"hidden" := "true")(
          div(className := "health-fill", style := scala.scalajs.js.Dynamic.literal(width = s"${game.hp * 10}%"))()
        ),
        div(className := "stat")(span("KEY"), strong(if (game.hasKey) "Collected" else "Not found")),
        div(className := "stat")(span("ENEMIES"), strong(s"${game.enemies.size} remaining")),
        p(className := "objective")("Find this floor’s key, then step onto 🚪 to descend. Your HP carries over. Walk into enemies to attack; potions restore 4 HP.")
      ),
      section(className := "card controls-card")(
        h2("Make your move"),
        div(className := "controls")(
          control("↑", Action.Move(Direction.Up), "up"),
          control("←", Action.Move(Direction.Left), "left"),
          control("Wait", Action.Wait, "wait"),
          control("→", Action.Move(Direction.Right), "right"),
          control("↓", Action.Move(Direction.Down), "down")
        ),
        p(className := "hint")("The dungeon moves only when you do.")
      ),
      section(className := "card journal", aria-"live" := "polite")(
        h2("Field notes"), ol(game.log.zipWithIndex.map { (entry, index) => li(key := index.toString)(entry) })
      ),
      div(className := s"result ${game.status.toString.toLowerCase}", role := "status")(
        game.status match {
          case Status.Playing => if (model.playback.nonEmpty) "Resolving turn…" else "Your next move awaits."
          case Status.Descending => f"Descending to floor ${game.floor + 1}%02d…"
          case Status.Lost => s"Expedition over. Reached Floor ${game.floor} in ${game.turns} turns."
        }
      ),
      button(className := "restart", disabled := (game.status == Status.Descending), onClick := (_ => dispatch(Restart)))("New expedition ↗")
    )
  }

  def main(args: Array[String]): Unit = run.unsafeRunAndForget()
  def run: IO[Unit] =
    IO { if (LinkingInfo.developmentMode) hot.initialize() } *>
      Browser.run(dom.document.getElementById("app"), Program(init, view, update, subscriptions))
}
