package stopwatch

import scala.util.chaining.*
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import scala.concurrent.duration.DurationInt

import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*

import cats.effect.IO

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import fui.*

object Main {

  //
  // MODEL
  //

  case class Model(
      startTime: Option[Long] = None,
      elapsedBefore: Long = 0,
      elapsed: Long = 0,
      active: Boolean = false
  ) {
    def elapsedTotal: Long = elapsedBefore + elapsed

    def toggle: Model = copy(active = !active)
  }

  def init(@scala.annotation.unused url: URL): (Model, Cmd[Msg]) = (Model(), Cmd.none)

  //
  // UPDATE
  //

  enum Msg {
    case ToggleActivation
    case StartTime(time: Long)
    case Tick(currentTime: Long)
    case Reset
  }

  export Msg.*

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case ToggleActivation =>
        model.toggle.pipe { model =>
          (
            model,
            if (model.active)
              IO(StartTime(System.currentTimeMillis())).toCmd
            else
              Cmd.none
          )
        }

      case StartTime(time) =>
        (
          model.copy(
            startTime = Some(time),
            elapsedBefore = model.elapsedTotal,
            elapsed = 0
          ),
          Cmd.none
        )

      case Tick(currentTime) =>
        (
          model.copy(elapsed =
            model.startTime.map(currentTime - _).getOrElse(0)
          ),
          Cmd.none
        )

      case Reset => (Model(), Cmd.none)
    }

  //
  // SUBSCRIPTIONS
  //

  def subscriptions(model: Model): Sub[Msg] =
    if (model.active) {
      Sub.every(10.millis, "tick").map(Tick(_))
    } else {
      Sub.Empty
    }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    val seconds = model.elapsedTotal.toDouble / 1000.0
    div(
      div(className := "elapsed-time")(
        span(className := "seconds")(f"$seconds%1.2f")
      ),
      div(className := "buttons")(
        button(onClick := ((_) => dispatch(ToggleActivation)))(
          if (model.active)
            "Stop"
          else
            "Start"
        ),
        button(onClick := ((_) => dispatch(Reset)))("Reset")
      )
    )
  }

  def main(args: Array[String]): Unit =
    run.unsafeRunAndForget()

  def run: IO[Unit] =
    Dispatcher.parallel[IO].use { dispatcher =>
      IO {
        if (LinkingInfo.developmentMode) {
          hot.initialize()
        }
      } *> {
        Browser.runProgram(
          dom.document.getElementById("app"),
          Program(init, view, update, subscriptions),
          dispatcher
        ).use(_ => IO.never)
      }
    }
}
