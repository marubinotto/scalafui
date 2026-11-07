package featureworkspace

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import marubinotto.fui.*
import org.scalajs.dom
import org.scalajs.dom.URL
import scala.scalajs.LinkingInfo
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*

object Main {
  object Slots extends FeatureSlots[Unit] {
    val left = slot(Workspace.feature)
    val right = slot(Workspace.feature)
  }
  enum Side {
    case Left, Right
  }
  case class Model(workspaces: Slots.State = Slots.empty)
  enum Msg {
    case WorkspaceEvent(event: Slots.Event)
    case Open(side: Side)
    case Close(side: Side)
    case Reset(side: Side)
  }

  def slotFor(side: Side): Slots.Slot[Unit, Workspace.Model, Workspace.Msg, Nothing] =
    side match {
      case Side.Left => Slots.left
      case Side.Right => Slots.right
    }

  val binding = Slots.bind[Model, Msg, Nothing](
    policy = Slots.policy(),
    state = _.workspaces,
    replace = (model, workspaces) => model.copy(workspaces = workspaces),
    wrap = Msg.WorkspaceEvent.apply,
    extract = { case Msg.WorkspaceEvent(event) => Some(event); case _ => None },
    onOutput = (_, model) => Transition.unchanged(model)
  )

  def init(@scala.annotation.unused url: URL): Transition[Model, Msg, Nothing] =
    binding.init(Model(), Slots.left, ())
      .andThen(model => binding.init(model, Slots.right, ()))

  def update(msg: Msg, model: Model): Transition[Model, Msg, Nothing] = msg match {
    case Msg.Open(side) if slotFor(side).model(model.workspaces).isEmpty =>
      binding.init(model, slotFor(side), ())
    case Msg.Reset(side) if slotFor(side).model(model.workspaces).isDefined =>
      binding.init(model, slotFor(side), ())
    case Msg.Close(side) => binding.remove(model, slotFor(side))
    case _ => Transition.unchanged(model)
  }

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    def workspace(side: Side, title: String, number: String): ReactElement = {
      val active = slotFor(side).model(model.workspaces).isDefined
      section(className := "workspace", aria-"label" := title)(
        header(className := "workspace-heading")(
          div(className := "workspace-title")(
            span(className := "workspace-number")(number), h2(title)
          ),
          if (active) div(className := "workspace-actions")(
            button(className := "text-button", onClick := (_ => dispatch(Msg.Reset(side))))("Reset workspace"),
            button(className := "text-button close-button", onClick := (_ => dispatch(Msg.Close(side))))("Close")
          ) else span(className := "closed-badge")("Closed")
        ),
        binding.view(model, slotFor(side), dispatch,
          div(className := "closed-workspace")(
            h3("Room for a fresh start."),
            p("Reopen with a new counter and a blank note."),
            button(className := "primary-button", onClick := (_ => dispatch(Msg.Open(side))))("Open workspace")
          )
        )
      )
    }
    div(className := "app-shell")(
      header(className := "page-heading")(
        p(className := "eyebrow")("SCALAFUI / INTERACTIVE EXAMPLE"),
        h1("One feature. Two workspaces."),
        p(className := "intro")("The same building blocks, with a life of their own. Try one side, then the other.")
      ),
      div(className := "workspaces")(
        workspace(Side.Left, "Left workspace", "01"),
        workspace(Side.Right, "Right workspace", "02")
      ),
      section(className := "try-it", aria-"label" := "Things to try")(
        div(h3("01 / Make it yours"), p("Count on the left. Write on the right. Each workspace keeps its own state.")),
        div(h3("02 / Change the view"), p("Switch Edit to Preview. Your note stays put, and the counter keeps going.")),
        div(h3("03 / Start fresh"), p("Click +1 after 3s, then reset or close that workspace. The old result won’t change the new one."))
      ),
      footer("Closing or resetting does not cancel work already running. Results from the previous instance are ignored.")
    )
  }

  val program = FeatureProgram(init, update, view).withFeatures(binding).program

  def main(args: Array[String]): Unit = run.unsafeRunAndForget()

  def run: IO[Unit] =
    IO { if (LinkingInfo.developmentMode) hot.initialize() } *>
      Browser.run(dom.document.getElementById("app"), program)
}
