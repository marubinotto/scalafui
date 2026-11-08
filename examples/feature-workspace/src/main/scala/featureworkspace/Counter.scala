package featureworkspace

import cats.effect.IO
import marubinotto.fui.*
import scala.concurrent.duration.*
import marubinotto.react.ReactNode
import marubinotto.react.html.*

object Counter extends Feature[Unit, Unit, Counter.Model, Counter.Msg, Nothing] {
  case class Model(value: Int = 0, pending: Boolean = false, autoIncrement: Boolean = false)

  enum Msg {
    case Increment, Decrement, AddLater, DelayedIncrement, ToggleAutoIncrement, Tick
  }

  def init(input: Unit, context: Unit): Transition[Model, Msg, Nothing] =
    Transition(Model())

  def update(msg: Msg, model: Model, context: Unit): Transition[Model, Msg, Nothing] =
    msg match {
      case Msg.ToggleAutoIncrement => Transition(model.copy(autoIncrement = !model.autoIncrement))
      case Msg.Tick if model.autoIncrement => Transition(model.copy(value = model.value + 1))
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

  override def subscriptions(model: Model, context: Unit): Sub[Msg] =
    if (model.autoIncrement) Sub.every(1.second, "tick").map(_ => Msg.Tick)
    else Sub.Empty

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
        )("+1 after 3s"),
        button(
          aria-"pressed" := model.autoIncrement.toString,
          onClick := (_ => dispatch(Msg.ToggleAutoIncrement))
        )(if (model.autoIncrement) "Stop auto +1" else "Start auto +1")
      ),
      p(className := "counter-status", role := "status")(
        (if (model.autoIncrement) "Auto +1 every second. " else "Auto increment is off. ") +
          (if (model.pending) "Waiting for a delayed +1…" else "Ready when you are.")
      )
    )
}
