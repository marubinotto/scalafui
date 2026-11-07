package featureworkspace

import marubinotto.fui.*
import marubinotto.react.ReactNode
import marubinotto.react.html.*

object NoteEditor extends Feature[String, Unit, String, NoteEditor.Msg, NoteEditor.Out] {
  enum Msg {
    case Changed(text: String)
  }
  enum Out {
    case TextChanged(text: String)
  }

  def init(input: String, context: Unit): Transition[String, Msg, Out] = Transition(input)

  def update(msg: Msg, model: String, context: Unit): Transition[String, Msg, Out] =
    msg match {
      case Msg.Changed(text) => Transition(text, outputs = List(Out.TextChanged(text)))
    }

  def view(model: String, context: Unit, dispatch: Msg => Unit): ReactNode =
    label(className := "note-editor")(
      span(className := "field-label")("Your note"),
      textarea(
        value := model,
        placeholder := "Write something for this workspace…",
        onChange := (event => dispatch(Msg.Changed(event.currentTarget.value)))
      )
    )
}
