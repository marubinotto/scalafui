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

/** A reusable definition, not a running instance or an independent store.
  * Input initializes a visit; Context supplies current read-only external data
  * on each call and is not retained by the child state container. Use Unit when
  * no context is needed. Context changes alone do not invoke update.
  */
trait Feature[Input, Context, Model, Msg, Out] { self =>
  def init(input: Input, context: Context): Transition[Model, Msg, Out]
  def update(
      msg: Msg,
      model: Model,
      context: Context
  ): Transition[Model, Msg, Out]
  def view(model: Model, context: Context, dispatch: Msg => Unit): ReactElement

  /** Adapt the caller's context without capturing a context value in state. */
  final def contramapContext[C](
      project: C => Context
  ): Feature[Input, C, Model, Msg, Out] =
    new Feature[Input, C, Model, Msg, Out] {
      def init(input: Input, context: C) = self.init(input, project(context))
      def update(msg: Msg, model: Model, context: C) =
        self.update(msg, model, project(context))
      def view(model: Model, context: C, dispatch: Msg => Unit) =
        self.view(model, project(context), dispatch)
      override private[fui] def childFeatureGroups = self.childFeatureGroups
    }

  final def init(input: Input)(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] =
    init(input, unit(()))
  final def update(msg: Msg, model: Model)(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] =
    update(msg, model, unit(()))
  final def view(model: Model, dispatch: Msg => Unit)(using
      unit: Unit =:= Context
  ): ReactElement =
    view(model, unit(()), dispatch)

  private[fui] def childFeatureGroups: Set[ChildFeatures[?]] = Set.empty

  final def withFeatures(
      child: FeatureBinding[Context, Model, Msg, Out]
  ): Feature[Input, Context, Model, Msg, Out] = {
    require(
      !childFeatureGroups.contains(child.features),
      "A ChildFeatures group can only be bound once within a feature"
    )
    new Feature[Input, Context, Model, Msg, Out] {
      def init(input: Input, context: Context) = self.init(input, context)
      def update(msg: Msg, model: Model, context: Context) =
        child.route(msg, model, context).getOrElse(
          self.update(msg, model, context)
        )
      def view(model: Model, context: Context, dispatch: Msg => Unit) =
        self.view(model, context, dispatch)
      override private[fui] def childFeatureGroups =
        self.childFeatureGroups + child.features
    }
  }
}

/** A typed connection between one ChildFeatures group and its parent's
  * state/messages.
  */
trait FeatureBinding[Context, Model, Msg, Out] {
  val features: ChildFeatures[?]
  protected def childContext(
      model: Model,
      context: Context
  ): features.ContextValue
  protected def policy: features.Policy
  protected def state(model: Model): features.State
  protected def replace(model: Model, state: features.State): Model
  protected def wrap(event: features.Event): Msg
  protected def extract(msg: Msg): Option[features.Event]
  protected def onOutput(
      output: features.Output,
      model: Model,
      context: Context
  ): Transition[Model, Msg, Out]

  final def open[I, M, E, O](
      model: Model,
      child: features.Child[I, M, E, O],
      input: I,
      context: Context
  ): Transition[Model, Msg, Out] =
    lift(
      model,
      context,
      features.open(
        state(model),
        child,
        input,
        policy,
        childContext(model, context)
      )
    )

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
      empty: => ReactElement,
      context: Context
  ): ReactElement =
    features.view(
      state(model),
      child,
      event => dispatch(wrap(event)),
      empty,
      childContext(model, context)
    )

  final def open[I, M, E, O](
      model: Model,
      child: features.Child[I, M, E, O],
      input: I
  )(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] = open(model, child, input, unit(()))

  final def view(
      model: Model,
      child: features.Child[?, ?, ?, ?],
      dispatch: Msg => Unit,
      empty: => ReactElement
  )(using
      unit: Unit =:= Context
  ): ReactElement = view(model, child, dispatch, empty, unit(()))

  private[fui] final def route(msg: Msg, model: Model)(using
      unit: Unit =:= Context
  ): Option[Transition[Model, Msg, Out]] =
    route(msg, model, unit(()))

  private def lift(
      model: Model,
      context: Context,
      change: Transition[features.State, features.Event, features.Output]
  ) = {
    val initial: Transition[Model, Msg, Out] =
      Transition(replace(model, change.model), change.cmd.map(wrap))
    change.outputs.foldLeft(initial) { (acc, output) =>
      val next = onOutput(output, acc.model, context)
      Transition(next.model, acc.cmd ++ next.cmd, acc.outputs ++ next.outputs)
    }
  }

  private[fui] final def route(
      msg: Msg,
      model: Model,
      context: Context
  ): Option[Transition[Model, Msg, Out]] =
    extract(msg).map { event =>
      if (features.accepts(event, state(model)))
        lift(
          model,
          context,
          features.update(event, state(model), childContext(model, context))
        )
      else Transition.unchanged(model)
    }
}
