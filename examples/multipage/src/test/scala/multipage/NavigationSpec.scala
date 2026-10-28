package multipage

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import fui.Browser
import multipage.page.Search
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("jsdom", "JSDOM")
class NavigationDOM(
    @scala.annotation.unused html: String,
    @scala.annotation.unused options: js.Object
) extends js.Object {
  val window: js.Dynamic = js.native
}

class NavigationSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  test("child outputs replace query URLs, navigate once, restore history and clear pending searches") {
    val requests = scala.collection.mutable.ArrayBuffer.empty[String]
    val environment = Resource.make(IO {
      val oldWindow = js.Dynamic.global.globalThis.window
      val oldDocument = js.Dynamic.global.globalThis.document
      val oldFetch = js.Dynamic.global.globalThis.fetch
      val env = new NavigationDOM("<div id='app'></div>", js.Dynamic.literal(url = "https://example.test/"))
      js.Dynamic.global.globalThis.window = env.window
      js.Dynamic.global.globalThis.document = env.window.document
      js.Dynamic.global.globalThis.fetch = ((url: String, _: js.Dynamic) => {
        requests += url
        val body =
          if (url.contains("search.json")) """{"docs":[{"key":"/works/one","title":"A book"}]}"""
          else if (url.contains("editions.json")) """{"entries":[]}"""
          else """{"key":"/works/one","title":"A book"}"""
        js.Promise.resolve(js.Dynamic.literal(
          ok = true, status = 200,
          text = (() => js.Promise.resolve(body)): js.Function0[js.Promise[String]]
        ))
      }): js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]]
      (env, oldWindow, oldDocument, oldFetch)
    }) { case (env, window, document, fetch) => IO.sleep(20.millis) *> IO {
      env.window.close()
      js.Dynamic.global.globalThis.window = window
      js.Dynamic.global.globalThis.document = document
      js.Dynamic.global.globalThis.fetch = fetch
    }}

    def awaitCondition(condition: => Boolean): IO[Unit] = IO.defer {
      if (condition) IO.unit else IO.sleep(5.millis) *> awaitCondition(condition)
    }

    environment.use { _ =>
      var last = Main.Model()
      val program = Main.program.copy(view = (model, dispatch) => {
        last = model
        Main.view(model, dispatch)
      })
      Browser.mount(dom.document.getElementById("app"), program).use { runtime =>
        def send(msg: Search.Msg): IO[Unit] = IO {
          runtime.dispatch(Main.Msg.PageEvent(Main.Pages.search.event(last.page, msg).get))
        }
        for {
          _ <- send(Search.Msg.QueryInput("scala"))
          _ <- awaitCondition(dom.window.location.search == "?query=scala")
          _ <- awaitCondition(Main.Pages.search.model(last.page).exists(_.works.isInstanceOf[LoadState.Loaded[?]]))
          _ <- send(Search.Msg.FoundItemClicked("one"))
          _ <- awaitCondition(dom.window.location.pathname == "/work/one")
          _ <- awaitCondition(Main.Pages.work.model(last.page).exists(m =>
            m.work.isInstanceOf[LoadState.Loaded[?]] && m.editions.isInstanceOf[LoadState.Loaded[?]]))
          _ <- IO(assert(requests.count(_.endsWith("/works/one.json")) == 1))
          _ <- IO(dom.window.history.back())
          _ <- awaitCondition(Main.Pages.search.model(last.page).exists(_.query == "scala"))
          _ <- send(Search.Msg.QueryInput("must-not-fetch"))
          _ <- awaitCondition(dom.window.location.search == "?query=must-not-fetch")
          _ <- send(Search.Msg.QueryInput(""))
          _ <- awaitCondition(dom.window.location.search.isEmpty)
          _ <- IO.sleep(550.millis)
          _ <- IO {
            assert(!requests.exists(_.contains("must-not-fetch")))
            assert(Main.Pages.search.model(last.page).exists(_.works == LoadState.Idle))
          }
          _ <- Browser.pushUrl("/unknown")
          _ <- awaitCondition(dom.document.getElementById("app").textContent.contains("Page Not Found"))
        } yield succeed
      }
    }.timeout(5.seconds).unsafeToFuture()
  }
}
