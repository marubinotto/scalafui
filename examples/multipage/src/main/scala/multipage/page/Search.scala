package multipage.page

import scala.concurrent.duration.*
import slinky.core.facade.ReactElement
import slinky.web.html.*

import fui.*
import cats.effect.IO

import multipage.domain
import multipage.Server
import multipage.LoadState
import multipage.Main.Route

object Search {

  //
  // MODEL
  //

  case class Model(
      query: String = "",
      works: LoadState[Seq[domain.Work]] = LoadState.Idle
  )

  def init(): (Model, Cmd[Msg]) = (Model(), Cmd.none)

  def init(query: String): (Model, Cmd[Msg]) =
    (
      Model(query = query, works = LoadState.Loading),
      Server.searchWorks(query).map(SearchResult(query, _)).toCmd
    )

  //
  // UPDATE
  //

  enum Msg {
    case QueryInput(query: String)
    case SearchResult(
        query: String,
        result: Either[Throwable, Seq[domain.Work]]
    )
    case FoundItemClicked(workId: String)
  }

  export Msg.*

  val DebounceKey = "search"

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case QueryInput(query) =>
        if (query.isBlank())
          (
            model.copy(
              query = query,
              works = LoadState.Idle
            ),
            Cmd.exec(IO.fromEither(Route.search.url(())).flatMap(Browser.replaceUrl))
              .debounce(DebounceKey, 1.millis) // Cancel previous search
          )
        else
          (
            model.copy(query = query, works = LoadState.Loading),
            Cmd.Batch(
              Cmd.exec(IO.fromEither(Route.searchWithQuery.url(query)).flatMap(Browser.replaceUrl)),
              Server.searchWorks(query)
                .map(SearchResult(query, _)).toCmd
                .debounce(DebounceKey, 500.millis)
            )
          )

      case SearchResult(query, Right(works)) => {
        println(s"Search results for '$query': ${works.size} works found")
        (
          model.copy(works = LoadState.Loaded(works)),
          Cmd.none
        )
      }

      case SearchResult(query, Left(error)) =>
        (
          model.copy(works = LoadState.Failed(error)),
          Cmd.none
        )

      case FoundItemClicked(workId) =>
        (model, Cmd.exec(IO.fromEither(Route.work.url(workId)).flatMap(url => Browser.pushUrl(url))))
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
          onChange := ((e) => dispatch(QueryInput(e.target.value)))
        )
      ),
      model.works match {
        case LoadState.Failed(error) => div(className := "error")(error.toString())
        case LoadState.Loading => div(className := "loading")("Loading...")
        case LoadState.Idle => div(className := "search-results")()
        case LoadState.Loaded(works) =>
          div(className := "search-results")(
            works.map(work =>
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
      }
    )
  }
}
