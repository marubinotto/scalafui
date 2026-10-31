package marubinotto.fui

import slinky.core.facade.ReactElement

/** Typed child registrations. Coexistence rules are supplied separately at
  * binding time.
  */
abstract class FeatureSet {
  final class State private[FeatureSet] (
      private[FeatureSet] val generations: Map[AnyRef, Long],
      private[FeatureSet] val entries: Map[AnyRef, Entry]
  )
  final class Event private[FeatureSet] (
      private[FeatureSet] val generation: Long,
      private[FeatureSet] val child: AnyRef,
      private[FeatureSet] val message: Any
  )
  final class Output private[FeatureSet] (
      private[FeatureSet] val child: AnyRef,
      private[FeatureSet] val value: Any
  )

  /** Initial state only. close/clear retain generation history on existing
    * states.
    */
  val empty: State = new State(Map.empty, Map.empty)

  final class Exclusive private[FeatureSet] (
      private[FeatureSet] val members: Set[AnyRef]
  )
  final class Policy private[FeatureSet] (
      private[FeatureSet] val peers: Map[AnyRef, Set[AnyRef]]
  )

  final def exclusive(children: Child[?, ?, ?, ?]*): Exclusive = {
    require(
      children.size >= 2,
      "An exclusive group must contain at least two children"
    )
    val members = children.toSet[AnyRef]
    require(
      members.size == children.size,
      "A child cannot occur twice in an exclusive group"
    )
    new Exclusive(members)
  }

  /** Unlisted children coexist. Exclusive groups must not overlap. */
  final def policy(groups: Exclusive*): Policy = {
    val members = groups.flatMap(_.members)
    require(
      members.distinct.size == members.size,
      "A child can belong to only one exclusive group"
    )
    new Policy(
      groups.flatMap(group =>
        group.members.map(key => key -> (group.members - key))
      ).toMap
    )
  }

  // Type erasure stays here. Child identity is checked before payload/model casts.
  private trait Entry {
    def model: Any
    def update(event: Event, state: State): Transition[State, Event, Output]
    def view(generation: Long, dispatch: Event => Unit): ReactElement
  }

  final class Child[I, M, E, O] private[FeatureSet] (
      feature: Feature[I, M, E, O]
  ) {
    child =>
    object output {
      def unapply(out: Output): Option[O] =
        Option.when(out.child eq child)(out.value.asInstanceOf[O])
    }

    def model(state: State): Option[M] =
      state.entries.get(child).map(_.model.asInstanceOf[M])

    /** Capture the destination of this particular visit. */
    def event(state: State, msg: E): Option[Event] =
      state.entries.get(child).map(_ =>
        new Event(state.generations(child), child, msg)
      )

    private[FeatureSet] def initialize(
        input: I,
        generation: Long,
        state: State
    ) =
      transition(feature.init(input), generation, state)

    private def transition(
        change: Transition[M, E, O],
        generation: Long,
        state: State
    ): Transition[State, Event, Output] =
      Transition(
        new State(
          state.generations.updated(child, generation),
          state.entries.updated(child, entry(change.model))
        ),
        change.cmd.map(msg => new Event(generation, child, msg)),
        change.outputs.map(out => new Output(child, out))
      )

    private def entry(value: M): Entry = new Entry {
      def model: Any = value
      def update(event: Event, state: State) =
        transition(
          feature.update(event.message.asInstanceOf[E], value),
          event.generation,
          state
        )
      def view(generation: Long, dispatch: Event => Unit) =
        feature.view(value, msg => dispatch(new Event(generation, child, msg)))
    }
  }

  final def child[I, M, E, O](feature: Feature[I, M, E, O]): Child[I, M, E, O] =
    new Child(feature)

  final def open[I, M, E, O](
      state: State,
      child: Child[I, M, E, O],
      input: I,
      policy: Policy
  ): Transition[State, Event, Output] = {
    val generation = Math.addExact(state.generations.getOrElse(child, 0L), 1L)
    val prepared = new State(
      state.generations,
      state.entries -- policy.peers.getOrElse(child, Set.empty)
    )
    child.initialize(input, generation, prepared)
  }

  final def close(state: State, child: Child[?, ?, ?, ?]): State =
    new State(state.generations, state.entries - child)

  final def clear(state: State): State = new State(state.generations, Map.empty)

  final def view(
      state: State,
      child: Child[?, ?, ?, ?],
      dispatch: Event => Unit,
      empty: => ReactElement
  ): ReactElement =
    state.entries.get(child).fold(empty)(
      _.view(state.generations(child), dispatch)
    )

  final def bind[M, E, O](
      policy: Policy,
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      onOutput: (Output, M) => Transition[M, E, O]
  ): FeatureBinding[M, E, O] { val features: FeatureSet.this.type } = {
    val rules = policy
    val get = state
    val set = replace
    val encode = wrap
    val decode = extract
    val output = onOutput
    new FeatureBinding[M, E, O] {
      val features: FeatureSet.this.type = FeatureSet.this
      protected def policy = rules
      protected def state(model: M) = get(model)
      protected def replace(model: M, value: State) = set(model, value)
      protected def wrap(event: Event) = encode(event)
      protected def extract(msg: E) = decode(msg)
      protected def onOutput(out: Output, model: M) = output(out, model)
    }
  }

  private[fui] def accepts(event: Event, state: State): Boolean =
    state.entries.contains(event.child) && state.generations.get(
      event.child
    ).contains(event.generation)

  private[fui] def update(
      event: Event,
      state: State
  ): Transition[State, Event, Output] =
    if (accepts(event, state)) state.entries(event.child).update(event, state)
    else Transition.unchanged(state)
}
