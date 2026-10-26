package fuidungeon

import scala.scalajs.LinkingInfo
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalajs.dom.URL
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*
import fui.*
import Game.*

object Main {
  case class Model(game: Option[State] = None)
  sealed trait Msg
  case class Ready(seed: Long) extends Msg
  case class FloorReady(seed: Long) extends Msg
  case class Act(action: Action) extends Msg
  case object Restart extends Msg

  private def newGame: Cmd[Msg] = Cmd.One(IO {
    Some(Ready(scala.util.Random.nextLong()))
  })
  def init(@annotation.unused url: URL): (Model, Cmd[Msg]) = (Model(), newGame)
  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) = msg match {
    case Ready(seed) if model.game.isEmpty => (Model(Some(initial(seed))), Cmd.none)
    case FloorReady(seed) if model.game.exists(_.status == Status.Descending) =>
      (model.copy(game = model.game.map(Game.nextFloor(_, seed))), Cmd.none)
    case Act(action) =>
      val next = model.game.map(Game.step(_, action))
      val descending = model.game.exists(_.status == Status.Playing) &&
        next.exists(_.status == Status.Descending)
      val command: Cmd[Msg] =
        if (descending) Cmd.One(IO { Some(FloorReady(scala.util.Random.nextLong())) })
        else Cmd.none
      (model.copy(game = next), command)
    case Restart if model.game.exists(_.status != Status.Descending) => (Model(), newGame)
    case _ => (model, Cmd.none)
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
        val playing = game.status == Status.Playing
        val wide = Camera.offset(game.board, game.player, 15)
        val narrow = Camera.offset(game.board, game.player, 9)
        val mapStyle = scala.scalajs.js.Dynamic.literal(
          "--map-width" -> s"${game.board.width * Camera.tileSize}px",
          "--map-height" -> s"${game.board.height * Camera.tileSize}px",
          "--wide-x" -> s"${wide.x}px", "--wide-y" -> s"${wide.y}px",
          "--narrow-x" -> s"${narrow.x}px", "--narrow-y" -> s"${narrow.y}px"
        )
        def control(label: String, action: Action, css: String): ReactElement =
          button(className := s"control $css", disabled := !playing,
            onClick := (_ => dispatch(Act(action))))(label)
        div(className := "game-layout")(
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
                  appearance =
                    if (position == game.player) ("🤠", "player", "You")
                    else if (game.enemies.exists(_.position == position)) ("👺", "enemy", "Goblin")
                    else game.items.get(position) match {
                      case Some(Item.Key) => ("🔑", "key", "Key")
                      case Some(Item.Potion) => ("🧪", "potion", "Healing potion")
                      case None if position == game.board.exit => ("🚪", "exit", "Next floor")
                      case None if !game.board.isFloor(position) => ("#", "wall", "Wall")
                      case _ => ("·", "floor", "Floor")
                    }
                } yield div(key := s"$x-$y", className := s"tile ${appearance._2}",
                  title := s"${appearance._3} ($x, $y)")(appearance._1))
              )
            ),
            p(className := "hint")("Click the map or Tab to focus · Arrow keys / WASD · Space to wait"),
            div(className := "legend")(
              span("🤠 You"), span("👺 Goblin"), span("🔑 Key"), span("🧪 Potion"), span("🚪 Next floor")
            )
          ),
          aside(className := "sidebar")(
            section(className := "card")(
              h2("Your expedition"),
              div(className := "stat")(span("HEALTH"), strong(s"${game.hp} / $maxHp")),
              div(className := "health-track", aria-"hidden" := "true")(
                div(className := "health-fill", style := scala.scalajs.js.Dynamic.literal(width = s"${game.hp * 10}%"))()
              ),
              div(className := "stat")(span("KEY"), strong(if (game.hasKey) "Collected" else "Not found")),
              div(className := "stat")(span("GOBLINS"), strong(s"${game.enemies.size} remaining")),
              p(className := "objective")("Find this floor’s key, then step onto 🚪 to descend. Your HP carries over. Walk into goblins to attack; potions restore 4 HP.")
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
                case Status.Playing => "Your next move awaits."
                case Status.Descending => f"Descending to floor ${game.floor + 1}%02d…"
                case Status.Lost => s"Expedition over. Reached Floor ${game.floor} in ${game.turns} turns."
              }
            ),
            button(className := "restart", disabled := (game.status == Status.Descending), onClick := (_ => dispatch(Restart)))("New expedition ↗")
          )
        )
      },
      footer("Built with Scalafui · Scala.js · One action, one turn")
    )

  def main(args: Array[String]): Unit = run.unsafeRunAndForget()
  def run: IO[Unit] = Dispatcher.parallel[IO].use { dispatcher =>
    IO { if (LinkingInfo.developmentMode) hot.initialize() } *>
      Browser.runProgram(dom.document.getElementById("app"), Program(init, view, update), dispatcher) *>
      IO.never
  }
}
