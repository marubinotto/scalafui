package featureworkspace

import marubinotto.fui.*
import slinky.core.facade.ReactElement
import slinky.web.html.*

object NotePreview extends Feature[String, Unit, String, Nothing, Nothing] {
  def init(input: String, context: Unit): Transition[String, Nothing, Nothing] = Transition(input)

  def update(msg: Nothing, model: String, context: Unit): Transition[String, Nothing, Nothing] =
    Transition.unchanged(model)

  def view(model: String, context: Unit, dispatch: Nothing => Unit): ReactElement =
    div(className := "note-preview")(
      span(className := "field-label")("Preview"),
      p(className := (if (model.isEmpty) "note-text empty-note" else "note-text"))(
        if (model.isEmpty) "Your note will appear here." else model
      )
    )
}
