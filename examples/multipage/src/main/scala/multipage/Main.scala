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
import cats.effect.std.Dispatcher
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

  case class Model(currentPage: Page) {
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
    case SearchPageMsg(pageMsg: page.Search.Msg)
    case WorkPageMsg(pageMsg: page.Work.Msg)
  }

  export Msg.*

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    (msg, model.currentPage) match {
      case (UrlChanged(url), _) =>
        applyUrlChange(url, model)

      case (SearchPageMsg(pageMsg), SearchPage(pageModel)) =>
        applyPageUpdate(
          page.Search.update(pageMsg, pageModel),
          SearchPage.apply,
          SearchPageMsg.apply,
          model
        )

      case (WorkPageMsg(pageMsg), WorkPage(pageModel)) =>
        applyPageUpdate(
          page.Work.update(pageMsg, pageModel),
          WorkPage.apply,
          WorkPageMsg.apply,
          model
        )

      case _ => (model, Cmd.none)
    }

  def applyUrlChange(url: URL, model: Model): (Model, Cmd[Msg]) =
    url.pathname + url.search + url.hash match {
      case Route.searchWithQuery(q) =>
        applyPageUpdate(page.Search.init(q), SearchPage.apply, SearchPageMsg.apply, model)

      case Route.search(_) =>
        applyPageUpdate(page.Search.init(), SearchPage.apply, SearchPageMsg.apply, model)

      case Route.work(id) =>
        applyPageUpdate(page.Work.init(id), WorkPage.apply, WorkPageMsg.apply, model)

      case _ =>
        (model.copy(currentPage = NotFoundPage), Cmd.none)
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
          page.Search.view(pageModel, mapDispatch(SearchPageMsg(_)))

        case WorkPage(pageModel) =>
          page.Work.view(pageModel, mapDispatch(WorkPageMsg(_)))
      }
    )
  }

  def main(args: Array[String]): Unit =
    run.unsafeRunAndForget()

  def run: IO[Unit] =
    Dispatcher.parallel[IO].use { dispatcher =>
      IO {
        if (LinkingInfo.developmentMode) {
          hot.initialize()
        }
      } *> {
        Browser.runProgram(
          dom.document.getElementById("app"),
          Program(
            init,
            view,
            update,
            (_: Model) => Sub.Empty,
            Some(UrlChanged(_))
          ),
          dispatcher
        ).use(_ => IO.never)
      }
    }
}
