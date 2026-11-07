package featureworkspace

import cats.effect.IO
import marubinotto.fui.*
import scala.concurrent.duration.*
import marubinotto.react.ReactNode
import marubinotto.react.html.*

object Counter extends Feature[Unit, Unit, Counter.Model, Counter.Msg, Nothing] {
  case class Model(value: Int = 0, pending: Boolean = false)

  enum Msg {
    case Increment, Decrement, AddLater, DelayedIncrement
  }

  def init(input: Unit, context: Unit): Transition[Model, Msg, Nothing] =
    Transition(Model())

  def update(msg: Msg, model: Model, context: Unit): Transition[Model, Msg, Nothing] =
    msg match {
      case Msg.Increment => Transition(model.copy(value = model.value + 1))
      case Msg.Decrement => Transition(model.copy(value = model.value - 1))
      case Msg.AddLater if !model.pending =>
        Transition(
          model.copy(pending = true),
          (IO.sleep(3.seconds) *> IO.pure(Msg.DelayedIncrement)).toCmd
        )
      case Msg.DelayedIncrement if model.pending =>
        Transition(model.copy(value = model.value + 1, pending = false))
      case _ => Transition.unchanged(model)
    }

  def view(model: Model, context: Unit, dispatch: Msg => Unit): ReactNode =
    div(className := "counter")(
      div(className := "counter-value")(model.value.toString),
      div(className := "counter-actions")(
        button(onClick := (_ => dispatch(Msg.Decrement)), aria-"label" := "Decrease counter")("−1"),
        button(onClick := (_ => dispatch(Msg.Increment)), aria-"label" := "Increase counter")("+1"),
        button(
          className := "delayed-button",
          disabled := model.pending,
          onClick := (_ => dispatch(Msg.AddLater))
        )("+1 after 3s")
      ),
      p(className := "counter-status", role := "status")(
        if (model.pending) "Waiting for a delayed +1…" else "Ready when you are."
      )
    )
}
