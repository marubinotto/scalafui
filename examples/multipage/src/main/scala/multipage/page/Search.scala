package multipage.page

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui._

import multipage.domain
import multipage.Server
import multipage.Main.Route

object Search {

  //
  // MODEL
  //

  case class Model(
      query: String = "",
      loading: Boolean = false,
      loadingError: Option[Throwable] = None,
      works: Seq[domain.Work] = Seq.empty
  )

  def init(): (Model, Cmd[Msg]) = (Model(), Cmd.none)

  def init(query: String): (Model, Cmd[Msg]) =
    (Model().copy(query = query), Browser.send(SendQuery))

  //
  // UPDATE
  //

  sealed trait Msg
  case class QueryInput(query: String) extends Msg
  case object SendQuery extends Msg
  case class SearchResult(result: Either[Throwable, Seq[domain.Work]])
      extends Msg
  case class FoundItemClicked(workId: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case QueryInput(query) =>
        (model.copy(query = query), Cmd.none)

      case SendQuery =>
        (
          model.copy(loading = true, loadingError = None),
          Browser
            .replaceUrl(Route.searchWithQuery.url(model.query))
            .flatMap(_ => Server.searchWorks(model.query).map(SearchResult(_)))
        )

      case SearchResult(Right(works)) =>
        (
          model.copy(loading = false, loadingError = None, works = works),
          Cmd.none
        )

      case SearchResult(Left(error)) =>
        (
          model.copy(loading = false, loadingError = Some(error)),
          Cmd.none
        )

      case FoundItemClicked(workId) =>
        (model, Browser.pushUrl(Route.work.url(workId)))
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
