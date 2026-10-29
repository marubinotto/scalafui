package marubinotto.fui

import org.scalatest.funsuite.AnyFunSuite
import org.scalajs.dom.URL
import slinky.web.html.div

object FeatureExamples {
  object Counter extends Feature[Int, Int, Int, Int] {
    def init(input: Int) = Transition(input, outputs = List(input))
    def update(msg: Int, model: Int) = Transition(model + msg, outputs = List(model + msg, msg))
    def view(model: Int, dispatch: Int => Unit) = div(model.toString)
  }
  object Left extends FeatureSlot { val counter = variant(Counter) }
  object Right extends FeatureSlot { val counter = variant(Counter) }
  case class Model(left: Left.State = Left.empty, right: Right.State = Right.empty, log: List[Int] = Nil)
  enum Msg {
    case LeftEvent(event: Left.Event)
    case RightEvent(event: Right.Event)
  }
  val left = Left.bind[Model, Msg, Nothing](
    _.left, (m, s) => m.copy(left = s), Msg.LeftEvent.apply,
    { case Msg.LeftEvent(e) => Some(e); case _ => None },
    (o, m) => o match {
      case Left.counter.output(value) => Transition(m.copy(log = m.log :+ value))
      case _ => Transition.unchanged(m)
    }
  )
  val right = Right.bind[Model, Msg, Nothing](
    _.right, (m, s) => m.copy(right = s), Msg.RightEvent.apply,
    { case Msg.RightEvent(e) => Some(e); case _ => None },
    (o, m) => o match {
      case Right.counter.output(value) => Transition(m.copy(log = m.log :+ value))
      case _ => Transition.unchanged(m)
    }
  )
  val feature: Feature[Unit, Model, Msg, Nothing] = new Feature[Unit, Model, Msg, Nothing] {
    def init(input: Unit) = {
      val first = left.open(Model(), Left.counter, 1)
      val second = right.open(first.model, Right.counter, 10)
      Transition(second.model, first.cmd ++ second.cmd)
    }
    def update(msg: Msg, model: Model): Transition[Model, Msg, Nothing] =
      throw new AssertionError("child messages must not reach the parent's update")
    def view(model: Model, dispatch: Msg => Unit) = div("counters")
  }.withFeatures(left).withFeatures(right)
}

class FeatureSpec extends AnyFunSuite {
  import FeatureExamples.*

  test("initial outputs see installed state and child output order is preserved") {
    val initial = feature.init(())
    assert(initial.model.log == List(1, 10))
    val event = Left.counter.event(initial.model.left, 3).get
    val next = feature.update(Msg.LeftEvent(event), initial.model)
    assert(Left.counter.model(next.model.left).contains(4))
    assert(Right.counter.model(next.model.right).contains(10))
    assert(next.model.log == List(1, 10, 4, 3))
  }

  test("clear preserves generations and reopening rejects previous instance events") {
    val initial = feature.init(()).model
    val old = Msg.LeftEvent(Left.counter.event(initial.left, 2).get)
    val cleared = left.clear(initial).model
    assert(feature.update(old, cleared) == Transition.unchanged(cleared))
    val reopened = left.open(cleared, Left.counter, 1).model
    assert(feature.update(old, reopened).model == reopened)
    val current = feature.update(Msg.LeftEvent(Left.counter.event(reopened.left, 2).get), reopened)
    assert(Left.counter.model(current.model.left).contains(3))
  }

  test("an initial output can remove its child from the resulting state") {
    val closing = Left.bind[Model, Msg, Nothing](
      _.left, (m, s) => m.copy(left = s), Msg.LeftEvent.apply,
      { case Msg.LeftEvent(e) => Some(e); case _ => None },
      (_, m) => Transition(m.copy(left = Left.clear(m.left)))
    )
    val next = closing.open(Model(), Left.counter, 3)
    assert(Left.counter.model(next.model.left).isEmpty)
  }

  test("nested slots reject events from a previous ancestor") {
    object Outer extends FeatureSlot { val counters = variant(feature) }
    val first = Outer.open(Outer.empty, Outer.counters, ())
    val inside = Outer.counters.model(first.model).get
    val childMsg = Msg.LeftEvent(Left.counter.event(inside.left, 1).get)
    val old = Outer.counters.event(first.model, childMsg).get
    val second = Outer.open(first.model, Outer.counters, ())
    assert(!Outer.accepts(old, second.model))
    val current = Outer.counters.model(second.model).get
    val msg = Msg.LeftEvent(Left.counter.event(current.left, 4).get)
    val next = Outer.update(Outer.counters.event(second.model, msg).get, second.model)
    assert(Left.counter.model(Outer.counters.model(next.model).get.left).contains(5))
    val changedInner = left.open(current, Left.counter, 20).model
    // Keep the outer visit, but replace an inner visit before its old message arrives.
    object Container extends FeatureSlot {
      val item = variant(new Feature[Model, Model, Msg, Nothing] {
        def init(input: Model): Transition[Model, Msg, Nothing] = Transition(input)
        def update(msg: Msg, model: Model) = feature.update(msg, model)
        def view(model: Model, dispatch: Msg => Unit) = div()
      })
    }
    val container = Container.open(Container.empty, Container.item, changedInner).model
    val staleInner = Msg.LeftEvent(Left.counter.event(current.left, 999).get)
    val addressed = Container.item.event(container, staleInner).get
    assert(Container.update(addressed, container) == Transition.unchanged(container))
  }

  test("a slot cannot be registered twice in the same parent") {
    intercept[IllegalArgumentException](feature.withFeatures(left))
  }

  test("slot addresses, states, variants and inputs are statically distinct") {
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      def wrong(event: Left.Event): Right.Event = event
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      Left.open(Left.empty, Right.counter, 1)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      Left.open(Left.empty, Left.counter, "wrong input")
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      left.open(Model(), Right.counter, 1)
    """)
  }

  test("FeatureProgram adapts to the existing Program interface") {
    val program = FeatureProgram[Model, Msg](
      _ => feature.init(()),
      (_, _) => throw new AssertionError("parent called"),
      (_, _) => div("root")
    ).withFeatures(left).withFeatures(right).program
    val model = program.init(new URL("https://example.test/"))._1
    val event = Msg.RightEvent(Right.counter.event(model.right, 2).get)
    val next = program.update(event, model)._1
    assert(Right.counter.model(next.right).contains(12))
  }
}
