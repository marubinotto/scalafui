package multipage

import org.scalatest.funsuite.AnyFunSuite
import org.scalajs.dom.URL
import marubinotto.fui.Transition
import multipage.page.{Search, Work}

class ResponseIdentitySpec extends AnyFunSuite {
  private val book = domain.Work("one", "Title", None, None)
  private val error = new Exception("Old request failed")

  test("out-of-order search successes and failures cannot replace the latest result") {
    val first = Search.update(Search.Msg.QueryInput("first"), Search.Model()).model
    val second = Search.update(Search.Msg.QueryInput("second"), first).model
    val loaded = Search.update(Search.Msg.SearchResult(second.requestSerial, Right(Seq(book))), second).model
    for (result <- Seq(Right(Seq(book)), Left(error))) {
      assert(Search.update(Search.Msg.SearchResult(first.requestSerial, result), loaded) == Transition.unchanged(loaded))
    }
  }

  test("repeating a query and clearing input both invalidate older requests") {
    val first = Search.init("same").model
    val other = Search.update(Search.Msg.QueryInput("other"), first).model
    val repeated = Search.update(Search.Msg.QueryInput("same"), other).model
    assert(Search.update(Search.Msg.SearchResult(first.requestSerial, Right(Seq(book))), repeated).model == repeated)
    val cleared = Search.update(Search.Msg.QueryInput(""), repeated).model
    assert(Search.update(Search.Msg.SearchResult(repeated.requestSerial, Left(error)), cleared).model == cleared)
    assert(Search.update(Search.Msg.SearchResult(repeated.requestSerial, Right(Seq(book))), cleared).model == cleared)
  }

  private def visit(path: String, model: Main.Model): Main.Model =
    Main.applyUrlChange(new URL("https://example.com" + path), model).model

  test("work responses belong to a page visit, including revisits to the same work") {
    val first = visit("/work/one", Main.Model())
    val other = visit("/work/two", first)
    val repeated = visit("/work/one", other)
    for {
      current <- Seq(other, repeated)
      response <- Seq(
        Work.Msg.WorkFetched(Right(book)),
        Work.Msg.WorkFetched(Left(error)),
        Work.Msg.EditionsFetched(Right(Seq.empty)),
        Work.Msg.EditionsFetched(Left(error))
      )
    } assert(Main.program.update(Main.Msg.PageEvent(Main.Pages.work.event(first.page, response).get), current)._1 == current)

    val accepted = Main.program.update(Main.Msg.PageEvent(Main.Pages.work.event(repeated.page, Work.Msg.WorkFetched(Right(book))).get), repeated)._1
    assert(Main.Pages.work.model(accepted.page).contains(Work.Model("one", work = LoadState.Loaded(book))))
  }

  test("search responses from a previous visit cannot update a new search page") {
    val first = visit("/?query=same", Main.Model())
    val work = visit("/work/one", first)
    val repeated = visit("/?query=same", work)
    val response = Search.Msg.SearchResult(0, Right(Seq(book)))
    assert(Main.program.update(Main.Msg.PageEvent(Main.Pages.search.event(first.page, response).get), repeated)._1 == repeated)
    val accepted = Main.program.update(Main.Msg.PageEvent(Main.Pages.search.event(repeated.page, response).get), repeated)._1
    assert(Main.Pages.search.model(accepted.page).contains(Search.Model("same", works = LoadState.Loaded(Seq(book)))))
  }
}
