package multipage

import org.scalatest.funsuite.AnyFunSuite
import org.scalajs.dom.URL
import fui.Cmd
import multipage.page.{Search, Work}

class ResponseIdentitySpec extends AnyFunSuite {
  private val book = domain.Work("one", "Title", None, None)
  private val error = new Exception("Old request failed")

  test("out-of-order search successes and failures cannot replace the latest result") {
    val first = Search.update(Search.Msg.QueryInput("first"), Search.Model())._1
    val second = Search.update(Search.Msg.QueryInput("second"), first)._1
    val loaded = Search.update(Search.Msg.SearchResult(second.requestSerial, Right(Seq(book))), second)._1
    for (result <- Seq(Right(Seq(book)), Left(error))) {
      assert(Search.update(Search.Msg.SearchResult(first.requestSerial, result), loaded) == (loaded, Cmd.none))
    }
  }

  test("repeating a query and clearing input both invalidate older requests") {
    val first = Search.init("same")._1
    val other = Search.update(Search.Msg.QueryInput("other"), first)._1
    val repeated = Search.update(Search.Msg.QueryInput("same"), other)._1
    assert(Search.update(Search.Msg.SearchResult(first.requestSerial, Right(Seq(book))), repeated)._1 == repeated)
    val cleared = Search.update(Search.Msg.QueryInput(""), repeated)._1
    assert(Search.update(Search.Msg.SearchResult(repeated.requestSerial, Left(error)), cleared)._1 == cleared)
    assert(Search.update(Search.Msg.SearchResult(repeated.requestSerial, Right(Seq(book))), cleared)._1 == cleared)
  }

  private def visit(path: String, model: Main.Model): Main.Model =
    Main.applyUrlChange(new URL("https://example.com" + path), model)._1

  test("work responses belong to a page visit, including revisits to the same work") {
    val first = visit("/work/one", Main.Model(Main.Page.NotFoundPage))
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
    } assert(Main.update(Main.Msg.WorkPageMsg(first.pageSerial, response), current) == (current, Cmd.none))

    val accepted = Main.update(Main.Msg.WorkPageMsg(repeated.pageSerial, Work.Msg.WorkFetched(Right(book))), repeated)._1
    assert(accepted.currentPage == Main.Page.WorkPage(Work.Model("one", work = LoadState.Loaded(book))))
  }

  test("search responses from a previous visit cannot update a new search page") {
    val first = visit("/?query=same", Main.Model(Main.Page.NotFoundPage))
    val work = visit("/work/one", first)
    val repeated = visit("/?query=same", work)
    val response = Search.Msg.SearchResult(0, Right(Seq(book)))
    assert(Main.update(Main.Msg.SearchPageMsg(first.pageSerial, response), repeated) == (repeated, Cmd.none))
    val accepted = Main.update(Main.Msg.SearchPageMsg(repeated.pageSerial, response), repeated)._1
    assert(accepted.currentPage == Main.Page.SearchPage(Search.Model("same", works = LoadState.Loaded(Seq(book)))))
  }
}
