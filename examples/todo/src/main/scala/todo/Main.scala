package todo

import scala.scalajs.LinkingInfo
import scala.scalajs.js
import scala.scalajs.js.timers._
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import org.scalajs.dom.URL

import cats.effect.IO
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

import fui._

@JSImport("/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

object Main {

  //
  // MODEL
  //

  case class Model(
      entries: Seq[Entry] = Seq.empty,
      taskInput: String = "",
      uid: Int = 0,
      visibility: String = "All"
  ) {
    def add(description: String): Model =
      this.copy(
        uid = uid + 1,
        taskInput = "",
        entries =
          if (description.trim().isEmpty())
            entries
          else
            entries :+ Entry(description, uid)
      )

    def setEditing(id: Int, isEditing: Boolean): Model =
      this.modify(_.entries.eachWhere(_.id == id).editing)
        .setTo(isEditing)

    def update(id: Int, description: String): Model =
      this.modify(_.entries.eachWhere(_.id == id).description)
        .setTo(description)

    def check(id: Int, isCompleted: Boolean): Model =
      this.modify(_.entries.eachWhere(_.id == id).completed)
        .setTo(isCompleted)

    def checkAll(isCompleted: Boolean): Model =
      this.modify(_.entries)
        .using(_.map(_.copy(completed = isCompleted)))

    def delete(id: Int): Model =
      this.modify(_.entries).using(_.filterNot(_.id == id))

    def deleteCompleted: Model =
      this.modify(_.entries).using(_.filterNot(_.completed))
  }

  case class Entry(
      description: String,
      completed: Boolean,
      editing: Boolean,
      id: Int
  )

  object Entry {
    def apply(description: String, id: Int): Entry =
      Entry(
        description = description,
        completed = false,
        editing = false,
        id = id
      )
  }

  def init(url: URL): (Model, Cmd[Msg]) = (Model(), Cmd.none)

  //
  // UPDATE
  //

  sealed trait Msg
  case object Add extends Msg
  case class UpdateInput(input: String) extends Msg
  case class EditingEntry(id: Int, isEditing: Boolean) extends Msg
  case class UpdateEntry(id: Int, description: String) extends Msg
  case class Delete(id: Int) extends Msg
  case object DeleteComplete extends Msg
  case class Check(id: Int, isCompleted: Boolean) extends Msg
  case class CheckAll(isCompleted: Boolean) extends Msg
  case class ChangeVisibility(visibility: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) = {
    msg match {
      case Add => (model.add(model.taskInput), Cmd.none)

      case UpdateInput(input) => (model.copy(taskInput = input), Cmd.none)

      case EditingEntry(id, isEditing) =>
        (
          model.setEditing(id, isEditing),
          Cmd(IO.async { cb =>
            IO {
              dom.document.getElementById("todo-" + id) match {
                case element: HTMLElement =>
                  setTimeout(100) {
                    element.focus()
                    cb(Right(None))
                  }
                case _ => cb(Right(None))
              }
              None
            }
          })
        )

      case UpdateEntry(id, description) =>
        (model.update(id, description), Cmd.none)

      case Delete(id) => (model.delete(id), Cmd.none)

      case DeleteComplete => (model.deleteCompleted, Cmd.none)

      case Check(id, isCompleted) => (model.check(id, isCompleted), Cmd.none)

      case CheckAll(isCompleted) => (model.checkAll(isCompleted), Cmd.none)

      case ChangeVisibility(visibility) =>
        (model.copy(visibility = visibility), Cmd.none)
    }
  }

  //
  // VIEW
  //

  val css = IndexCSS

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    div(className := "todomvc-wrapper")(
      section(className := "todoapp")(
        viewInput(model.taskInput, dispatch),
        viewEntries(model.entries, model.visibility, dispatch),
        viewControls(model.entries, model.visibility, dispatch)
      ),
      infoFooter()
    )
  }

  def viewInput(taskInput: String, dispatch: Msg => Unit): ReactElement = {
    header(className := "header")(
      h1("todos"),
      input(
        className := "new-todo",
        placeholder := "What needs to be done?",
        autoFocus := true,
        value := taskInput,
        name := "newTodo",
        onInput := ((e) => dispatch(UpdateInput(e.target.value))),
        onKeyDown := ((e) => {
          if (e.keyCode == 13) dispatch(Add) else ()
        })
      )
    )
  }

  def viewEntries(
      entries: Seq[Entry],
      visibility: String,
      dispatch: Msg => Unit
  ): ReactElement = {
    val allCompleted = entries.forall(_.completed)
    val cssVisibility = if (entries.isEmpty) "hidden" else "visible"

    def isVisible(entry: Entry) =
      visibility match {
        case "Completed" => entry.completed
        case "Active"    => !entry.completed
        case _           => true
      }

    section(
      className := "main",
      style := js.Dynamic.literal(visibility = cssVisibility)
    )(
      input(
        className := "toggle-all",
        id := "toggle-all",
        `type` := "checkbox",
        name := "toggle",
        checked := allCompleted,
        onClick := ((e) => dispatch(CheckAll(!allCompleted)))
      ),
      label(htmlFor := "toggle-all")("Mark all as complete"),
      ul(className := "todo-list")(
        entries.filter(isVisible(_)).map(entry => viewEntry(entry, dispatch))
      )
    )
  }

  def viewEntry(entry: Entry, dispatch: Msg => Unit): ReactElement = {
    li(
      className := optionalClasses(
        Seq(("completed", entry.completed), ("editing", entry.editing))
      ),
      key := entry.id.toString()
    )(
      div(className := "view")(
        input(
          className := "toggle",
          `type` := "checkbox",
          checked := entry.completed,
          onClick := ((e) => dispatch(Check(entry.id, !entry.completed)))
        ),
        label(onDoubleClick := ((e) => dispatch(EditingEntry(entry.id, true))))(
          entry.description
        ),
        button(
          className := "destroy",
          onClick := ((e) => dispatch(Delete(entry.id)))
        )
      ),
      input(
        className := "edit",
        value := entry.description,
        name := "title",
        id := "todo-" + entry.id,
        onInput := ((e) => dispatch(UpdateEntry(entry.id, e.target.value))),
        onBlur := ((e) => dispatch(EditingEntry(entry.id, false))),
        onKeyDown := ((e) => {
          if (e.keyCode == 13) dispatch(EditingEntry(entry.id, false)) else ()
        })
      )
    )
  }

  def viewControls(
      entries: Seq[Entry],
      visibility: String,
      dispatch: Msg => Unit
  ): ReactElement = {
    val entriesCompleted = entries.count(_.completed)
    val entriesLeft = entries.size - entriesCompleted

    footer(className := "footer", hidden := entries.isEmpty)(
      viewControlsCount(entriesLeft),
      viewControlsFilters(visibility, dispatch),
      viewControlsClear(entriesCompleted, dispatch)
    )
  }

  def viewControlsCount(entriesLeft: Int): ReactElement = {
    span(className := "todo-count")(
      strong(entriesLeft.toString()),
      (if (entriesLeft == 1) " item" else " items") + " left"
    )
  }

  def viewControlsFilters(
      visibility: String,
      dispatch: Msg => Unit
  ): ReactElement = {
    ul(className := "filters")(
      visibilitySwap("#/", "All", visibility, dispatch),
      " ",
      visibilitySwap("#/active", "Active", visibility, dispatch),
      " ",
      visibilitySwap("#/completed", "Completed", visibility, dispatch)
    )
  }

  def visibilitySwap(
      uri: String,
      visibility: String,
      actualVisibility: String,
      dispatch: Msg => Unit
  ): ReactElement = {
    li(onClick := ((e) => dispatch(ChangeVisibility(visibility))))(
      a(
        href := uri,
        className := optionalClasses(
          Seq(("selected", visibility == actualVisibility))
        )
      )(visibility)
    )
  }

  def viewControlsClear(
      entriesCompleted: Int,
      dispatch: Msg => Unit
  ): ReactElement = {
    button(
      className := "clear-completed",
      hidden := (entriesCompleted == 0),
      onClick := ((e) => dispatch(DeleteComplete))
    )("Clear completed (" + entriesCompleted + ")")
  }

  def infoFooter(): ReactElement = {
    footer(className := "info")(
      p("Double-click to edit a todo")
    )
  }

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update)
    )
  }
}
