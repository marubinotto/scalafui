package marubinotto.fui

import slinky.core.facade.ReactElement

/** A pure state transition. Outputs are delivered to the owner in list order.
  */
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

  private[fui] def featureSets: Set[FeatureSet] = Set.empty

  final def withFeatures(
      child: FeatureBinding[Model, Msg, Out]
  ): Feature[Input, Model, Msg, Out] = {
    require(
      !featureSets.contains(child.features),
      "A FeatureSet can only be bound once within a feature"
    )
    new Feature[Input, Model, Msg, Out] {
      def init(input: Input) = self.init(input)
      def update(msg: Msg, model: Model) =
        child.route(msg, model).getOrElse(self.update(msg, model))
      def view(model: Model, dispatch: Msg => Unit) = self.view(model, dispatch)
      override private[fui] def featureSets = self.featureSets + child.features
    }
  }
}

/** A typed connection between one FeatureSet and its parent's state/messages.
  */
trait FeatureBinding[Model, Msg, Out] {
  val features: FeatureSet
  protected def policy: features.Policy
  protected def state(model: Model): features.State
  protected def replace(model: Model, state: features.State): Model
  protected def wrap(event: features.Event): Msg
  protected def extract(msg: Msg): Option[features.Event]
  protected def onOutput(
      output: features.Output,
      model: Model
  ): Transition[Model, Msg, Out]

  final def open[I, M, E, O](
      model: Model,
      child: features.Child[I, M, E, O],
      input: I
  ): Transition[Model, Msg, Out] =
    lift(model, features.open(state(model), child, input, policy))

  final def close(
      model: Model,
      child: features.Child[?, ?, ?, ?]
  ): Transition[Model, Msg, Out] =
    Transition(replace(model, features.close(state(model), child)))

  final def clear(model: Model): Transition[Model, Msg, Out] =
    Transition(replace(model, features.clear(state(model))))

  final def view(
      model: Model,
      child: features.Child[?, ?, ?, ?],
      dispatch: Msg => Unit,
      empty: => ReactElement
  ): ReactElement =
    features.view(state(model), child, event => dispatch(wrap(event)), empty)

  private def lift(
      model: Model,
      change: Transition[features.State, features.Event, features.Output]
  ) = {
    val initial: Transition[Model, Msg, Out] =
      Transition(replace(model, change.model), change.cmd.map(wrap))
    change.outputs.foldLeft(initial) { (acc, output) =>
      val next = onOutput(output, acc.model)
      Transition(next.model, acc.cmd ++ next.cmd, acc.outputs ++ next.outputs)
    }
  }

  private[fui] final def route(
      msg: Msg,
      model: Model
  ): Option[Transition[Model, Msg, Out]] =
    extract(msg).map(event => lift(model, features.update(event, state(model))))
}
