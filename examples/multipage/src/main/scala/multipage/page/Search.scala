package multipage.page

import scala.concurrent.duration.*
import slinky.core.facade.ReactElement
import slinky.web.html.*

import marubinotto.fui.*
import cats.effect.IO

import multipage.domain
import multipage.Server
import multipage.LoadState

object Search extends Feature[String, Search.Model, Search.Msg, Search.Out] {

  //
  // MODEL
  //

  case class Model(
      query: String = "",
      requestSerial: Long = 0,
      works: LoadState[Seq[domain.Work]] = LoadState.Idle
  )

  def init(): Transition[Model, Msg, Out] = Transition(Model())

  def init(query: String): Transition[Model, Msg, Out] =
    if (query.isBlank) Transition(Model(query = query))
    else Transition(
      Model(query = query, works = LoadState.Loading),
      Server.searchWorks(query).map(SearchResult(0, _)).toCmd
    )

  //
  // UPDATE
  //

  enum Msg {
    case QueryInput(query: String)
    case SearchResult(
        requestSerial: Long,
        result: Either[Throwable, Seq[domain.Work]]
    )
    case FoundItemClicked(workId: String)
  }

  enum Out {
    case QueryChanged(query: String)
    case OpenWork(workId: String)
  }

  export Msg.*

  val DebounceKey = "search"

  def update(msg: Msg, model: Model): Transition[Model, Msg, Out] =
    msg match {
      case QueryInput(query) =>
        val serial = model.requestSerial + 1
        if (query.isBlank())
          Transition(
            model.copy(query = query, requestSerial = serial, works = LoadState.Idle),
            // Replace a pending debounce without starting another request.
            Cmd.exec(IO.unit).debounce(DebounceKey, 1.millis),
            List(Out.QueryChanged(query))
          )
        else
          Transition(
            model.copy(query = query, requestSerial = serial, works = LoadState.Loading),
            Server.searchWorks(query)
              .map(SearchResult(serial, _)).toCmd
              .debounce(DebounceKey, 500.millis),
            List(Out.QueryChanged(query))
          )

      case SearchResult(serial, _) if serial != model.requestSerial =>
        Transition.unchanged(model)

      case SearchResult(_, Right(works)) =>
        Transition(model.copy(works = LoadState.Loaded(works)))

      case SearchResult(_, Left(error)) =>
        Transition(model.copy(works = LoadState.Failed(error)))

      case FoundItemClicked(workId) =>
        Transition(model, outputs = List(Out.OpenWork(workId)))
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
