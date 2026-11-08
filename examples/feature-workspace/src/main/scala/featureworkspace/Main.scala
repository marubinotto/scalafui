package featureworkspace

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import marubinotto.fui.*
import org.scalajs.dom
import org.scalajs.dom.URL
import marubinotto.react.ReactNode
import marubinotto.react.html.*

object Main {
  object Slots extends FeatureSlots[Unit] {
    val workspaces = collectionSlot[Int](Workspace.feature)
  }
  case class Model(
      workspaces: Slots.State = Slots.empty,
      cards: Vector[Int] = Vector(1, 2),
      nextId: Int = 3
  )
  enum Msg {
    case WorkspaceEvent(event: Slots.Event)
    case Add
    case Open(id: Int)
    case Close(id: Int)
    case Reset(id: Int)
    case Remove(id: Int)
  }

  val binding = Slots.bind[Model, Msg, Nothing](
    state = _.workspaces,
    replace = (model, workspaces) => model.copy(workspaces = workspaces),
    wrap = Msg.WorkspaceEvent.apply,
    extract = { case Msg.WorkspaceEvent(event) => Some(event); case _ => None },
    onOutput = (_, model) => Transition.unchanged(model)
  )

  def init(@scala.annotation.unused url: URL): Transition[Model, Msg, Nothing] =
    binding.init(Model(), Slots.workspaces, 1, ())
      .andThen(model => binding.init(model, Slots.workspaces, 2, ()))

  def update(msg: Msg, model: Model): Transition[Model, Msg, Nothing] = msg match {
    case Msg.Add =>
      val id = model.nextId
      binding.init(model.copy(cards = model.cards :+ id, nextId = Math.addExact(id, 1)), Slots.workspaces, id, ())
    case Msg.Open(id) if model.cards.contains(id) && !binding.isActive(model, Slots.workspaces, id) =>
      binding.init(model, Slots.workspaces, id, ())
    case Msg.Reset(id) if binding.isActive(model, Slots.workspaces, id) =>
      binding.init(model, Slots.workspaces, id, ())
    case Msg.Close(id) => binding.remove(model, Slots.workspaces, id)
    case Msg.Remove(id) =>
      binding.remove(model.copy(cards = model.cards.filterNot(_ == id)), Slots.workspaces, id)
    case _ => Transition.unchanged(model)
  }

  def view(model: Model, dispatch: Msg => Unit): ReactNode = {
    def workspace(id: Int): ReactNode = {
      val title = s"Workspace $id"
      val active = binding.isActive(model, Slots.workspaces, id)
      section(className := "workspace", aria-"label" := title)(
        header(className := "workspace-heading")(
          div(className := "workspace-title")(
            span(className := "workspace-number")(id.toString), h2(title)
          ),
          if (active) div(className := "workspace-actions")(
            button(className := "text-button", onClick := (_ => dispatch(Msg.Reset(id))))("Reset workspace"),
            button(className := "text-button close-button", onClick := (_ => dispatch(Msg.Close(id))))("Close")
          ) else span(className := "closed-badge")("Closed")
        ),
        binding.view(model, Slots.workspaces, id, dispatch,
          div(className := "closed-workspace")(
            h3("Room for a fresh start."),
            p("Reopen with a new counter and a blank note."),
            button(className := "primary-button", onClick := (_ => dispatch(Msg.Open(id))))("Reopen"),
            button(className := "text-button", onClick := (_ => dispatch(Msg.Remove(id))))("Remove")
          )
        )
      ).withKey(id.toString)
    }
    div(className := "app-shell")(
      header(className := "page-heading")(
        p(className := "eyebrow")("SCALAFUI / INTERACTIVE EXAMPLE"),
        h1("One feature. More workspaces."),
        p(className := "intro")("Add workspaces, give each its own counter and note, then close or start them fresh.")
      ),
      button(className := "primary-button add-workspace", onClick := (_ => dispatch(Msg.Add)))("Add workspace"),
      div(className := "workspaces")(model.cards.map(workspace)*),
      section(className := "try-it", aria-"label" := "Things to try")(
        div(h3("01 / Make it yours"), p("Add a workspace. Count and write independently in each card.")),
        div(h3("02 / Change the view"), p("Start auto +1 in several cards. Stop one, or switch its note view; the others keep counting.")),
        div(h3("03 / Start fresh"), p("Click +1 after 3s, close and reopen that workspace. The old result won’t change it. Close and remove cards you no longer need."))
      ),
      footer("Closing or resetting stops auto increment. A delayed +1 keeps running, but its old result is ignored.")
    )
  }

  val program = FeatureProgram(init, update, view).withFeatures(binding).program

  def main(args: Array[String]): Unit = run.unsafeRunAndForget()

  def run: IO[Unit] =
    IO.unit *>
      Browser.run(dom.document.getElementById("app"), program)
}
