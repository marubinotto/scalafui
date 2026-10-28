package fui

import slinky.core.facade.ReactElement

/** A logical state position containing zero or one child. Not an execution scope. */
abstract class FeatureSlot {
  final class State private[FeatureSlot] (
      private[FeatureSlot] val generation: Long,
      private[FeatureSlot] val entry: Option[Entry]
  )
  final class Event private[FeatureSlot] (
      private[FeatureSlot] val generation: Long,
      private[FeatureSlot] val variant: AnyRef,
      private[FeatureSlot] val message: Any
  )
  final class Output private[FeatureSlot] (
      private[FeatureSlot] val variant: AnyRef,
      private[FeatureSlot] val value: Any
  )

  /** Initial state only. Use clear on an existing state to retain its generation. */
  val empty: State = new State(0L, None)

  // Type erasure is confined here: an Event/Output is constructed by its Variant,
  // and variant identity is checked before any payload or model is cast.
  private trait Entry {
    def variant: AnyRef
    def model: Any
    def update(event: Event, generation: Long): Transition[State, Event, Output]
    def view(generation: Long, dispatch: Event => Unit): ReactElement
  }

  final class Variant[I, M, E, O] private[FeatureSlot] (feature: Feature[I, M, E, O]) {
    variant =>
    object output {
      def unapply(out: Output): Option[O] =
        Option.when(out.variant eq variant)(out.value.asInstanceOf[O])
    }

    def model(state: State): Option[M] =
      state.entry.filter(_.variant eq variant).map(_.model.asInstanceOf[M])

    /** Capture an address to this particular visit, also useful in pure tests. */
    def event(state: State, msg: E): Option[Event] =
      state.entry.filter(_.variant eq variant)
        .map(_ => new Event(state.generation, variant, msg))

    private[FeatureSlot] def initialize(input: I, generation: Long) =
      transition(feature.init(input), generation)

    private def transition(change: Transition[M, E, O], generation: Long): Transition[State, Event, Output] = {
      Transition(
        new State(generation, Some(entry(change.model))),
        change.cmd.map(msg => new Event(generation, variant, msg)),
        change.outputs.map(out => new Output(variant, out))
      )
    }

    private def entry(value: M): Entry = new Entry {
      def variant: AnyRef = Variant.this
      def model: Any = value
      def update(event: Event, generation: Long) =
        transition(feature.update(event.message.asInstanceOf[E], value), generation)
      def view(generation: Long, dispatch: Event => Unit) =
        feature.view(value, msg => dispatch(new Event(generation, variant, msg)))

    }
  }

  final def variant[I, M, E, O](feature: Feature[I, M, E, O]): Variant[I, M, E, O] =
    new Variant(feature)

  final def open[I, M, E, O](state: State, variant: Variant[I, M, E, O], input: I)
      : Transition[State, Event, Output] =
    variant.initialize(input, Math.addExact(state.generation, 1L))

  /** Remove the instance without resetting the monotonically increasing generation. */
  final def clear(state: State): State = new State(state.generation, None)

  final def view(state: State, dispatch: Event => Unit, empty: => ReactElement): ReactElement =
    state.entry.fold(empty)(_.view(state.generation, dispatch))

  final def bind[M, E, O](
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      onOutput: (Output, M) => Transition[M, E, O]
  ): FeatureBinding[M, E, O] { val slot: FeatureSlot.this.type } = {
    val get = state
    val set = replace
    val encode = wrap
    val decode = extract
    val output = onOutput
    new FeatureBinding[M, E, O] {
      val slot: FeatureSlot.this.type = FeatureSlot.this
      protected def state(model: M) = get(model)
      protected def replace(model: M, value: State) = set(model, value)
      protected def wrap(event: Event) = encode(event)
      protected def extract(msg: E) = decode(msg)
      protected def onOutput(out: Output, model: M) = output(out, model)
    }
  }

  private[fui] def accepts(event: Event, state: State): Boolean =
    event.generation == state.generation &&
      state.entry.exists(e => e.variant eq event.variant)

  private[fui] def update(event: Event, state: State): Transition[State, Event, Output] =
    if (accepts(event, state)) {
      val next = state.entry.get.update(event, state.generation)
      // Preserve the state when a nested slot ignores a stale message.
      if (next.model.entry.get.model == state.entry.get.model) next.copy(model = state)
      else next
    } else Transition.unchanged(state)

}
