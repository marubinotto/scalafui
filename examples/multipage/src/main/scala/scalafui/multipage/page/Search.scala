package scalafui.multipage.page

import cats.effect.IO

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import scalafui.FunctionalUI._
import scalafui.multipage.Domain
import scalafui.multipage.Server
import scalafui.multipage.Main.Route

object Search {

  //
  // MODEL
  //

  case class Model(
      query: String,
      loading: Boolean,
      loadingError: Option[Throwable],
      works: Seq[Domain.Work]
  )

  def init(): (Model, Seq[Cmd[Msg]]) =
    (Model("", false, None, Seq.empty), Seq.empty)

  def init(query: String): (Model, Seq[Cmd[Msg]]) =
    (Model(query, false, None, Seq.empty), Seq(IO(Some(SendQuery))))

  //
  // UPDATE
  //

  sealed trait Msg
  case class QueryInput(query: String) extends Msg
  case object SendQuery extends Msg
  case class SearchResult(result: Either[Throwable, Seq[Domain.Work]])
      extends Msg
  case class FoundItemClicked(workId: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case QueryInput(query) =>
        (model.copy(query = query), Seq.empty)

      case SendQuery =>
        (
          model.copy(loading = true, loadingError = None),
          Seq(
            Browser
              .replaceUrl(Route.searchWithQuery.url(model.query))
              .flatMap((_: Option[Msg]) =>
                Server.searchWorks(model.query, SearchResult(_))
              )
          )
        )

      case SearchResult(Right(works)) =>
        (
          model.copy(loading = false, loadingError = None, works = works),
          Seq.empty
        )

      case SearchResult(Left(error)) =>
        (
          model.copy(loading = false, loadingError = Some(error)),
          Seq.empty
        )

      case FoundItemClicked(workId) =>
        (model, Seq(Browser.pushUrl(Route.work.url(workId))))
    }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    div(className := "search")(
      h1("Book Search"),
      div(className := "search-query")(
        input(
          `type` := "text",
          placeholder := "Title or Author Name (e.g. Haruki)",
          value := model.query,
          onInput := ((e) => dispatch(QueryInput(e.target.value)))
        ),
        button(
          disabled := model.query.trim().isEmpty() || model.loading,
          onClick := ((e) => dispatch(SendQuery))
        )("Search")
      ),
      model.loadingError.map(error =>
        div(className := "error")(error.toString())
      ),
      if (model.loading)
        div(className := "loading")("Loading...")
      else
        div(className := "search-results")(
          model.works.map(work =>
            div(className := "found-item", key := work.id.toString())(
              a(
                className := "title",
                href := "#",
                onClick := ((e) => {
                  e.preventDefault()
                  dispatch(FoundItemClicked(work.id))
                })
              )(work.title),
              div(className := "author")(work.authors.map(_.mkString(", ")))
            )
          )
        )
    )
  }
}
