package scalafui.stopwatch

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import scala.concurrent.duration.DurationInt

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

import cats.effect.IO

import scalafui.FunctionalUI._

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
  }

  def init(url: URL): (Model, Cmds[Msg]) = (Model(), Seq.empty)

  //
  // UPDATE
  //

  sealed trait Msg
  case object ToggleActivation extends Msg
  case class StartTime(time: Long) extends Msg
  case class Tick(currentTime: Long) extends Msg
  case object Reset extends Msg

  def update(msg: Msg, model: Model): (Model, Cmds[Msg]) =
    msg match {
      case ToggleActivation =>
        (
          model.copy(active = !model.active),
          if (!model.active)
            Seq(IO { Some(StartTime(System.currentTimeMillis())) })
          else
            Seq.empty
        )

      case StartTime(time) =>
        (
          model.copy(
            startTime = Some(time),
            elapsedBefore = model.elapsedTotal,
            elapsed = 0
          ),
          Seq.empty
        )

      case Tick(currentTime) =>
        (
          model.copy(elapsed =
            model.startTime.map(currentTime - _).getOrElse(0)
          ),
          Seq.empty
        )

      case Reset =>
        (Model(), Seq.empty)
    }

  //
  // SUBSCRIPTIONS
  //

  def subscriptions(model: Model): Sub[Msg] = {
    if (model.active) {
      Sub.every(10.millis, "tick").map(Tick(_))
    } else {
      Sub.Empty
    }
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
        button(onClick := ((e) => dispatch(ToggleActivation)))(
          if (model.active)
            "Stop"
          else
            "Start"
        ),
        button(onClick := ((e) => dispatch(Reset)))("Reset")
      )
    )
  }

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions)
    )
  }
}
