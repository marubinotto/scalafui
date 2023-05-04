package scalafui.multipage

import scala.util.chaining._

import scala.scalajs.LinkingInfo
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.dom.URL

import cats.effect.IO

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

import trail._

import scalafui.FunctionalUI._

@JSImport("/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

object Main {

  //
  // ROUTE
  //

  object Route {
    val search = Root
    val searchWithQuery = Root & Param[String]("query")
    val work = Root / "work" / Arg[String]()
  }

  //
  // MODEL
  //

  case class Model(currentPage: Page)

  sealed trait Page
  case object NotFoundPage extends Page
  case class SearchPage(pageModel: page.Search.Model) extends Page
  case class WorkPage(pageModel: page.Work.Model) extends Page

  def init(url: URL): (Model, Seq[Cmd[Msg]]) =
    applyUrlChange(url, Model(NotFoundPage))

  def updatePage(page: Page, model: Model): Model =
    model.copy(currentPage = page)

  //
  // UPDATE
  //

  sealed trait Msg
  case class UrlChanged(url: URL) extends Msg
  case class SearchPageMsg(pageMsg: page.Search.Msg) extends Msg
  case class WorkPageMsg(pageMsg: page.Work.Msg) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    (msg, model.currentPage) match {
      case (UrlChanged(url), _) =>
        applyUrlChange(url, model)

      case (SearchPageMsg(pageMsg), SearchPage(pageModel)) =>
        applyPageUpdate(
          page.Search.update(pageMsg, pageModel),
          SearchPage,
          SearchPageMsg,
          model
        )

      case (WorkPageMsg(pageMsg), WorkPage(pageModel)) =>
        applyPageUpdate(
          page.Work.update(pageMsg, pageModel),
          WorkPage,
          WorkPageMsg,
          model
        )

      case _ => (model, Seq())
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.searchWithQuery(q) =>
        applyPageUpdate(page.Search.init(q), SearchPage, SearchPageMsg, model)

      case Route.search(_) =>
        applyPageUpdate(page.Search.init(), SearchPage, SearchPageMsg, model)

      case Route.work(id) =>
        applyPageUpdate(page.Work.init(id), WorkPage, WorkPageMsg, model)

      case _ =>
        (model.copy(currentPage = NotFoundPage), Seq())
    }

  def applySubUpdate[SubModel, SubMsg](
      subUpdate: (SubModel, Seq[Cmd[SubMsg]]),
      applyModel: SubModel => Model,
      applyMsg: SubMsg => Msg
  ): (Model, Seq[Cmd[Msg]]) = {
    val (subModel, subCmds) = subUpdate
    (
      applyModel(subModel),
      subCmds.map(subCmd =>
        subCmd.flatMap(optSubMsg =>
          IO(optSubMsg.flatMap(subMsg => Some(applyMsg(subMsg))))
        )
      )
    )
  }

  def applyPageUpdate[PageModel, PageMsg](
      subUpdate: (PageModel, Seq[Cmd[PageMsg]]),
      wrapModel: PageModel => Page,
      applyMsg: PageMsg => Msg,
      model: Model
  ): (Model, Seq[Cmd[Msg]]) =
    applySubUpdate[PageModel, PageMsg](
      subUpdate,
      wrapModel(_).pipe(updatePage(_, model)),
      applyMsg
    )

  //
  // VIEW
  //

  val css = IndexCSS

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    def mapDispatch[SubMsg](wrap: SubMsg => Msg) =
      (subMsg: SubMsg) => dispatch(wrap(subMsg))

    div(className := "app")(
      div(className := "app-header")(),
      model.currentPage match {
        case NotFoundPage =>
          div(className := "page-not-found")("Page Not Found")

        case SearchPage(pageModel) =>
          page.Search.view(pageModel, mapDispatch(SearchPageMsg(_)))

        case WorkPage(pageModel) =>
          page.Work.view(pageModel, mapDispatch(WorkPageMsg(_)))
      }
    )
  }

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(
        init,
        view,
        update,
        (model: Model) => Sub.Empty,
        Some(UrlChanged(_))
      )
    )
  }
}
