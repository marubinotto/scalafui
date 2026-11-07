package featureworkspace

import marubinotto.fui.*
import marubinotto.react.html.*

object Workspace {
  object Slots extends FeatureSlots[Unit] {
    val counter = slot(Counter)
    val editor = slot(NoteEditor)
    val preview = slot(NotePreview)
  }

  case class Model(note: String = "", children: Slots.State = Slots.empty)

  enum Msg {
    case ChildEvent(event: Slots.Event)
    case Edit, Preview, ResetCounter
  }

  val binding = Slots.bind[Model, Msg, Nothing](
    policy = Slots.policy(Slots.exclusive(Slots.editor, Slots.preview)),
    state = _.children,
    replace = (model, children) => model.copy(children = children),
    wrap = Msg.ChildEvent.apply,
    extract = { case Msg.ChildEvent(event) => Some(event); case _ => None },
    onOutput = (output, model) => output match {
      case Slots.editor.output(NoteEditor.Out.TextChanged(text)) =>
        Transition(model.copy(note = text))
      case _ => Transition.unchanged(model)
    }
  )

  // Register the composed feature, so nested events go through this binding.
  val feature = Feature.withoutContext[Unit, Model, Msg, Nothing](
    init = _ =>
      binding.init(Model(), Slots.counter, ())
        .andThen(model => binding.init(model, Slots.editor, "")),

    update = (msg, model) =>
      msg match {
        case Msg.Edit if Slots.editor.model(model.children).isEmpty =>
          binding.init(model, Slots.editor, model.note)
        case Msg.Preview if Slots.preview.model(model.children).isEmpty =>
          binding.init(model, Slots.preview, model.note)
        // Reinitialize the slot, rather than just replacing the counter value:
        // replies addressed to its previous visit must be ignored.
        case Msg.ResetCounter => binding.init(model, Slots.counter, ())
        case _ => Transition.unchanged(model)
      },

    view = (model, dispatch) => {
      val editing = Slots.editor.model(model.children).isDefined
      div(className := "workspace-content")(
        section(className := "feature-panel")(
          div(className := "panel-heading")(
            h3("Counter"),
            button(className := "text-button", onClick := (_ => dispatch(Msg.ResetCounter)))("Reset counter")
          ),
          binding.view(model, Slots.counter, dispatch, div())
        ),
        section(className := "feature-panel note-panel")(
          div(className := "panel-heading")(
            h3("Notes"),
            div(className := "mode-switch", aria-"label" := "Note mode")(
              button(className := (if (editing) "selected" else ""), aria-"pressed" := editing.toString,
                onClick := (_ => dispatch(Msg.Edit)))("Edit"),
              button(className := (if (!editing) "selected" else ""), aria-"pressed" := (!editing).toString,
                onClick := (_ => dispatch(Msg.Preview)))("Preview")
            )
          ),
          binding.view(model, Slots.editor, dispatch, div()),
          binding.view(model, Slots.preview, dispatch, div()),
          p(className := "note-hint")("Switch views. Your note stays with this workspace.")
        )
      )
    }
  ).withFeatures(binding)
}
