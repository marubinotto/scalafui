package scalafui.multipage.page

import scala.scalajs.js

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import scalafui.FunctionalUI._
import scalafui.multipage.Domain
import scalafui.multipage.Server

object Work {

  //
  // MODEL
  //

  case class Model(
      workId: String,
      work: Option[Domain.Work],
      loadingWork: Boolean,
      loadingWorkError: Option[Throwable],
      editions: Seq[Domain.Edition],
      loadingEditions: Boolean,
      loadingEditionsError: Option[Throwable]
  )

  def init(workId: String): (Model, Seq[Cmd[Msg]]) =
    (
      Model(workId, None, true, None, Seq.empty, true, None),
      Seq(
        Server.fetchWork(workId, result => WorkFetched(result)),
        Server.fetchEditions(workId, result => EditionsFetched(result))
      )
    )

  //
  // UPDATE
  //

  sealed trait Msg
  case class WorkFetched(result: Either[Throwable, Domain.Work]) extends Msg
  case class EditionsFetched(result: Either[Throwable, Seq[Domain.Edition]])
      extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case WorkFetched(Right(work)) =>
        (model.copy(loadingWork = false, work = Some(work)), Seq.empty)

      case WorkFetched(Left(error)) =>
        (
          model.copy(loadingWork = false, loadingWorkError = Some(error)),
          Seq.empty
        )

      case EditionsFetched(Right(editions)) =>
        (model.copy(loadingEditions = false, editions = editions), Seq.empty)

      case EditionsFetched(Left(error)) =>
        (
          model.copy(
            loadingEditions = false,
            loadingEditionsError = Some(error)
          ),
          Seq.empty
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

  def viewWork(work: Domain.Work): ReactElement =
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

  def viewEdition(edition: Domain.Edition): ReactElement =
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
