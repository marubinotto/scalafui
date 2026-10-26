package fui

import scala.scalajs.js
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite

class BrowserSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  test("localStorage commands emit missing values and persist/remove entries") {
    val values = scala.collection.mutable.Map.empty[String, String]
    val storage = js.Dynamic.literal(
      getItem = ((key: String) => values.getOrElse(key, null)): js.Function1[String, String],
      setItem = ((key: String, value: String) => { values.update(key, value); () }): js.Function2[String, String, Unit],
      removeItem = ((key: String) => { values.remove(key); () }): js.Function1[String, Unit]
    )
    js.Dynamic.global.globalThis.window = js.Dynamic.literal(localStorage = storage)
    (for {
      missing <- Browser.getLocalStorage("draft").io
      saved <- Browser.setLocalStorage[Unit]("draft", "hello").io
      loaded <- Browser.getLocalStorage("draft").io
      removed <- Browser.removeLocalStorage[Unit]("draft").io
      after <- Browser.getLocalStorage("draft").io
    } yield {
      assert(missing.contains(None))
      assert(saved.isEmpty && removed.isEmpty)
      assert(loaded.contains(Some("hello")))
      assert(after.contains(None))
    }).unsafeToFuture()
  }

  test("HEAD returns the response, including non-success HTTP status") {
    val response = js.Dynamic.literal(status = 404)
    js.Dynamic.global.globalThis.fetch = ((url: String, init: js.Dynamic) => {
      assert(url == "/missing")
      assert(init.method.asInstanceOf[String] == "HEAD")
      js.Promise.resolve[js.Dynamic](response)
    }): js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]]
    Browser.ajaxHead("/missing").io.map { result =>
      assert(result.get.toOption.get.status == 404)
    }.unsafeToFuture()
  }

  test("HEAD reports network failure as a message") {
    js.Dynamic.global.globalThis.fetch = ((_: String, _: js.Dynamic) =>
      js.Promise.reject(new js.Error("offline"))
    ): js.Function2[String, js.Dynamic, js.Promise[Nothing]]
    Browser.ajaxHead("/offline").io.map(result => assert(result.get.isLeft)).unsafeToFuture()
  }
}
