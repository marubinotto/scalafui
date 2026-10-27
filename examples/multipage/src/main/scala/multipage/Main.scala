package multipage

import scala.util.chaining.*

import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*

import fui.router.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import fui.*

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

  case class Model(currentPage: Page, pageSerial: Long = 0) {
    def setPage(page: Page): Model =
      this.copy(currentPage = page)
  }

  enum Page {
    case NotFoundPage
    case SearchPage(pageModel: page.Search.Model)
    case WorkPage(pageModel: page.Work.Model)
  }

  export Page.*

  def init(url: URL): (Model, Cmd[Msg]) =
    applyUrlChange(url, Model(NotFoundPage))

  //
  // UPDATE
  //

  enum Msg {
    case UrlChanged(url: URL)
    case SearchPageMsg(pageSerial: Long, pageMsg: page.Search.Msg)
    case WorkPageMsg(pageSerial: Long, pageMsg: page.Work.Msg)
  }

  export Msg.*

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    (msg, model.currentPage) match {
      case (UrlChanged(url), _) =>
        applyUrlChange(url, model)

      case (SearchPageMsg(serial, pageMsg), SearchPage(pageModel)) if serial == model.pageSerial =>
        applyPageUpdate(
          page.Search.update(pageMsg, pageModel),
          SearchPage.apply,
          SearchPageMsg(model.pageSerial, _),
          model
        )

      case (WorkPageMsg(serial, pageMsg), WorkPage(pageModel)) if serial == model.pageSerial =>
        applyPageUpdate(
          page.Work.update(pageMsg, pageModel),
          WorkPage.apply,
          WorkPageMsg(model.pageSerial, _),
          model
        )

      case _ => (model, Cmd.none)
    }

  def applyUrlChange(url: URL, model: Model): (Model, Cmd[Msg]) = {
    // Each visit owns its commands, including revisits to the same URL.
    val nextModel = model.copy(pageSerial = model.pageSerial + 1)
    url.pathname + url.search + url.hash match {
      case Route.searchWithQuery(q) =>
        applyPageUpdate(page.Search.init(q), SearchPage.apply, SearchPageMsg(nextModel.pageSerial, _), nextModel)

      case Route.search(_) =>
        applyPageUpdate(page.Search.init(), SearchPage.apply, SearchPageMsg(nextModel.pageSerial, _), nextModel)

      case Route.work(id) =>
        applyPageUpdate(page.Work.init(id), WorkPage.apply, WorkPageMsg(nextModel.pageSerial, _), nextModel)

      case _ =>
        (nextModel.copy(currentPage = NotFoundPage), Cmd.none)
    }
  }

  def applyPageUpdate[PageModel, PageMsg](
      pageUpdate: (PageModel, Cmd[PageMsg]),
      wrapModel: PageModel => Page,
      applyMsg: PageMsg => Msg,
      model: Model
  ): (Model, Cmd[Msg]) = {
    val (pageModel, pageCmd) = pageUpdate
    (
      wrapModel(pageModel).pipe(model.setPage(_)),
      pageCmd.map(applyMsg)
    )
  }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    def mapDispatch[SubMsg](wrap: SubMsg => Msg) =
      (subMsg: SubMsg) => dispatch(wrap(subMsg))

    div(className := "app")(
      div(className := "app-header")(),
      model.currentPage match {
        case NotFoundPage =>
          div(className := "page-not-found")("Page Not Found")

        case SearchPage(pageModel) =>
          page.Search.view(pageModel, mapDispatch(SearchPageMsg(model.pageSerial, _)))

        case WorkPage(pageModel) =>
          page.Work.view(pageModel, mapDispatch(WorkPageMsg(model.pageSerial, _)))
      }
    )
  }

  def main(args: Array[String]): Unit =
    run.unsafeRunAndForget()

  def run: IO[Unit] =
    IO {
      if (LinkingInfo.developmentMode) {
        hot.initialize()
      }
    } *> {
      Browser.run(
        dom.document.getElementById("app"),
        Program(
          init,
          view,
          update,
          (_: Model) => Sub.Empty,
          Some(UrlChanged(_))
        )
      )
    }
}
