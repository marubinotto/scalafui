package multipage

import org.scalatest.funsuite.AnyFunSuite
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import slinky.core.facade.ReactElement
import multipage.page.{Search, Work}

@js.native
@JSImport("react-dom/server", "renderToStaticMarkup")
private object renderMarkup extends js.Function1[ReactElement, String] {
  def apply(element: ReactElement): String = js.native
}

class LoadStateSpec extends AnyFunSuite {
  test("a failed work request shows its error without leaving the work loading indicator") {
    val failed = Work.update(Work.Msg.WorkFetched(Left(new Exception("Work unavailable"))), Work.Model("one"))._1
    val html = renderMarkup(Work.view(failed, _ => ()))
    assert(html.contains("Work unavailable"))
    assert(!html.contains("Loading the work description"))
    assert(html.contains("Loading editions"))
  }

  test("work and editions can finish independently") {
    val work = domain.Work("one", "Title", None, None)
    val loaded = Work.update(Work.Msg.WorkFetched(Right(work)), Work.Model("one"))._1
    val failed = Work.update(Work.Msg.EditionsFetched(Left(new Exception("Editions unavailable"))), loaded)._1
    val html = renderMarkup(Work.view(failed, _ => ()))
    assert(html.contains("Title"))
    assert(html.contains("Editions unavailable"))
    assert(!html.contains("Loading"))
  }

  test("search failure, retry, empty result and cleared input have distinct displays") {
    val failed = Search.update(Search.Msg.SearchResult(0, Left(new Exception("Search unavailable"))),
      Search.Model(query = "one", works = LoadState.Loading))._1
    assert(renderMarkup(Search.view(failed, _ => ())).contains("Search unavailable"))
    val retry = Search.update(Search.Msg.QueryInput("two"), failed)._1
    val retryHtml = renderMarkup(Search.view(retry, _ => ()))
    assert(retryHtml.contains("Loading..."))
    assert(!retryHtml.contains("Search unavailable"))
    val empty = Search.update(Search.Msg.SearchResult(retry.requestSerial, Right(Seq.empty)), retry)._1
    assert(empty.works == LoadState.Loaded(Seq.empty))
    assert(!renderMarkup(Search.view(empty, _ => ())).contains("Loading..."))
    val cleared = Search.update(Search.Msg.QueryInput(" "), retry)._1
    assert(cleared.works == LoadState.Idle)
  }
}
