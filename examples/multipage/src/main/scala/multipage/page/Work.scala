package multipage.page

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html.*

import marubinotto.fui.*

import multipage.domain
import multipage.Server
import multipage.LoadState

object Work extends Feature[String, Work.Model, Work.Msg, Nothing] {

  //
  // MODEL
  //

  case class Model(
      workId: String,
      work: LoadState[domain.Work] = LoadState.Loading,
      editions: LoadState[Seq[domain.Edition]] = LoadState.Loading
  )

  def init(workId: String): Transition[Model, Msg, Nothing] =
    Transition(
      Model(workId),
      Cmd.Batch(
        Server.fetchWork(workId).map(WorkFetched(_)).toCmd,
        Server.fetchEditions(workId).map(EditionsFetched(_)).toCmd
      )
    )

  //
  // UPDATE
  //

  enum Msg {
    case WorkFetched(result: Either[Throwable, domain.Work])
    case EditionsFetched(result: Either[Throwable, Seq[domain.Edition]])
  }

  export Msg.*

  def update(msg: Msg, model: Model): Transition[Model, Msg, Nothing] =
    msg match {
      case WorkFetched(Right(work)) =>
        Transition(model.copy(work = LoadState.Loaded(work)), Cmd.none)

      case WorkFetched(Left(error)) =>
        Transition(model.copy(work = LoadState.Failed(error)), Cmd.none)

      case EditionsFetched(Right(editions)) =>
        Transition(model.copy(editions = LoadState.Loaded(editions)), Cmd.none)

      case EditionsFetched(Left(error)) =>
        Transition(model.copy(editions = LoadState.Failed(error)), Cmd.none)
    }

  //
  // VIEW
  //

  def view(model: Model, @scala.annotation.unused dispatch: Msg => Unit): ReactElement =
    div(className := "work-container")(
      model.work match {
        case LoadState.Loaded(work) => viewWork(work)
        case LoadState.Failed(error) => div(className := "error")(error.toString())
        case LoadState.Loading => div(className := "loading")("Loading the work description...")
        case LoadState.Idle => div()
      },
      div(className := "editions-container")(
        div(className := "header")("Editions"),
        model.editions match {
          case LoadState.Loaded(editions) =>
            div(className := "editions")(editions.map(viewEdition(_)))
          case LoadState.Failed(error) =>
            div(className := "error")(error.toString())
          case LoadState.Loading =>
            div(className := "editions")(div(className := "loading")("Loading editions..."))
          case LoadState.Idle => div()
        }
      )
    )

  def viewWork(work: domain.Work): ReactElement =
    div(className := "work")(
      h1(className := "title")(work.title),
      div(className := "author")(work.authors.map(_.mkString(", "))),
      work.description.map(description =>
        div(
          className := "description",
          dangerouslySetInnerHTML := js.Dynamic.literal(__html = description)
        )
      )
    )

  def viewEdition(edition: domain.Edition): ReactElement =
    div(className := "edition")(
      div(className := "title")(edition.title),
      div(className := "covers")(
        edition.coverIds.map(
          _.map(id =>
            div(className := "cover")(
              img(src := s"https://covers.openlibrary.org/b/id/$id-M.jpg")
            )
          )
        )
      ),
      div(className := "date")(
        s"Publish Date: ${edition.publishDate.getOrElse("")}"
      ),
      div(className := "publishers")(
        s"Publishers: ${edition.publishers.map(_.mkString(", ")).getOrElse("")}"
      )
    )
}
