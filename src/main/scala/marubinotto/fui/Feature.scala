package marubinotto.fui

import slinky.core.facade.ReactElement

/** A pure state transition. Outputs are delivered to the owner in list order. */
case class Transition[Model, +Msg, +Out](
    model: Model,
    cmd: Cmd[Msg] = Cmd.none,
    outputs: List[Out] = Nil
)

object Transition {
  def unchanged[Model](model: Model): Transition[Model, Nothing, Nothing] =
    Transition(model)
}

/** A reusable definition, not a running instance or an independent store. */
trait Feature[Input, Model, Msg, Out] { self =>
  def init(input: Input): Transition[Model, Msg, Out]
  def update(msg: Msg, model: Model): Transition[Model, Msg, Out]
  def view(model: Model, dispatch: Msg => Unit): ReactElement

  private[fui] def slots: Set[FeatureSlot] = Set.empty

  final def withFeatures(child: FeatureBinding[Model, Msg, Out]): Feature[Input, Model, Msg, Out] = {
    require(!slots.contains(child.slot), "A slot can only be bound once within a feature")
    new Feature[Input, Model, Msg, Out] {
      def init(input: Input) = self.init(input)
      def update(msg: Msg, model: Model) =
        child.route(msg, model).getOrElse(self.update(msg, model))
      def view(model: Model, dispatch: Msg => Unit) = self.view(model, dispatch)
      override private[fui] def slots = self.slots + child.slot
    }
  }
}

/** A typed connection between one slot and its parent's state/messages. */
trait FeatureBinding[Model, Msg, Out] {
  val slot: FeatureSlot
  protected def state(model: Model): slot.State
  protected def replace(model: Model, state: slot.State): Model
  protected def wrap(event: slot.Event): Msg
  protected def extract(msg: Msg): Option[slot.Event]
  protected def onOutput(output: slot.Output, model: Model): Transition[Model, Msg, Out]

  final def open[I, M, E, O](
      model: Model, variant: slot.Variant[I, M, E, O], input: I
  ): Transition[Model, Msg, Out] =
    lift(model, slot.open(state(model), variant, input))

  final def clear(model: Model): Transition[Model, Msg, Out] =
    Transition(replace(model, slot.clear(state(model))))

  final def view(model: Model, dispatch: Msg => Unit, empty: => ReactElement): ReactElement =
    slot.view(state(model), event => dispatch(wrap(event)), empty)

  private def lift(model: Model, change: Transition[slot.State, slot.Event, slot.Output]) = {
    val initial: Transition[Model, Msg, Out] =
      Transition(replace(model, change.model), change.cmd.map(wrap))
    change.outputs.foldLeft(initial) { (acc, output) =>
      val next = onOutput(output, acc.model)
      Transition(next.model, acc.cmd ++ next.cmd, acc.outputs ++ next.outputs)
    }
  }

  private[fui] final def route(msg: Msg, model: Model): Option[Transition[Model, Msg, Out]] =
    extract(msg).map(event => lift(model, slot.update(event, state(model))))
}
