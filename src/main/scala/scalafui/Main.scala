package scalafui

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import cats.effect.IO

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

import scalafui.{FunctionalUI => FUI}

object Main {

  //
  // MODEL
  //

  case class Model(messages: Seq[String], input: String)

  def init(url: URL): (Model, FUI.Cmds[Msg]) =
    (Model(Seq.empty, ""), Seq())

  //
  // UPDATE
  //

  sealed trait Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

  def update(msg: Msg, model: Model): (Model, FUI.Cmds[Msg]) = {
    msg match {
      case Input(input) =>
        (model.copy(input = input), Seq.empty)

      case Send =>
        (
          model.copy(messages = model.messages :+ model.input, input = ""),
          Seq.empty
        )
    }
  }

  //
  // SUBSCRIPTIONS
  //

  def subscriptions(model: Model): FUI.Subs[Msg] = Map()

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    div(className := "app")(
      h1(className := "app-title")("Welcome to Functional UI!"),
      div(className := "message-input")(
        input(
          value := model.input,
          onInput := ((e) => dispatch(Input(e.target.value)))
        ),
        button(onClick := ((e) => dispatch(Send)))("Send")
      ),
      div(className := "messages")(
        model.messages.map(message => div(className := "message")(message))
      )
    )
  }

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    FUI.Browser.runProgram(
      dom.document.getElementById("app"),
      FUI.Program(init, view, update, subscriptions)
    )
  }
}
