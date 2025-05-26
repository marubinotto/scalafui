package multipage.page

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui._

import multipage.domain
import multipage.Server

object Work {

  //
  // MODEL
  //

  case class Model(
      workId: String,
      work: Option[domain.Work] = None,
      loadingWork: Boolean = true,
      loadingWorkError: Option[Throwable] = None,
      editions: Seq[domain.Edition] = Seq.empty,
      loadingEditions: Boolean = true,
      loadingEditionsError: Option[Throwable] = None
  )

  def init(workId: String): (Model, Cmd[Msg]) =
    (
      Model(workId),
      Cmd.Batch(
        Server.fetchWork(workId).map(WorkFetched(_)),
        Server.fetchEditions(workId).map(EditionsFetched(_))
      )
    )

  //
  // UPDATE
  //

  sealed trait Msg
  case class WorkFetched(result: Either[Throwable, domain.Work]) extends Msg
  case class EditionsFetched(result: Either[Throwable, Seq[domain.Edition]])
      extends Msg

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case WorkFetched(Right(work)) =>
        (model.copy(loadingWork = false, work = Some(work)), Cmd.none)

      case WorkFetched(Left(error)) =>
        (
          model.copy(loadingWork = false, loadingWorkError = Some(error)),
          Cmd.none
        )

      case EditionsFetched(Right(editions)) =>
        (model.copy(loadingEditions = false, editions = editions), Cmd.none)

      case EditionsFetched(Left(error)) =>
        (
          model.copy(
            loadingEditions = false,
            loadingEditionsError = Some(error)
          ),
          Cmd.none
        )
    }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    div(className := "work-container")(
      model.loadingWorkError.map(error =>
        div(className := "error")(error.toString())
      ),
      model.work
        .map(viewWork(_))
        .getOrElse(
          div(className := "loading")("Loading the work description...")
        ),
      div(className := "editions-container")(
        div(className := "header")("Editions"),
        model.loadingEditionsError.map(error =>
          div(className := "error")(error.toString())
        ),
        div(className := "editions")(
          if (model.loadingEditions)
            div(className := "loading")("Loading editions...")
          else
            model.editions.map(viewEdition(_))
        )
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
