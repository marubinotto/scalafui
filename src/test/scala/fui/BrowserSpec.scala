package fui

import scala.scalajs.js
import cats.effect.{IO, Resource}
import org.scalajs.dom
import io.circe.Decoder
import scala.concurrent.duration.*
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite

class BrowserSpec extends AsyncFunSuite with org.scalatest.BeforeAndAfterAll {
  private var previousWindow: js.Any = js.undefined
  private var previousFetch: js.Any = js.undefined

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    previousWindow = js.Dynamic.global.globalThis.window
    previousFetch = js.Dynamic.global.globalThis.fetch
  }

  override protected def afterAll(): Unit = {
    js.Dynamic.global.globalThis.window = previousWindow
    js.Dynamic.global.globalThis.fetch = previousFetch
    super.afterAll()
  }

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
      missing <- Browser.getLocalStorage("draft")
      _ <- Browser.setLocalStorage("draft", "hello")
      loaded <- Browser.getLocalStorage("draft")
      _ <- Browser.removeLocalStorage("draft")
      after <- Browser.getLocalStorage("draft")
    } yield {
      assert(missing.isEmpty)
      assert(loaded.contains("hello"))
      assert(after.isEmpty)
    }).unsafeToFuture()
  }

  test("HEAD returns the response, including non-success HTTP status") {
    val response = js.Dynamic.literal(status = 404)
    js.Dynamic.global.globalThis.fetch = ((url: String, init: js.Dynamic) => {
      assert(url == "/missing")
      assert(init.method.asInstanceOf[String] == "HEAD")
      js.Promise.resolve[js.Dynamic](response)
    }): js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]]
    Browser.ajaxHead("/missing").map { result =>
      assert(result.toOption.get.status == 404)
    }.unsafeToFuture()
  }

  test("HEAD reports network failure as a message") {
    js.Dynamic.global.globalThis.fetch = ((_: String, _: js.Dynamic) =>
      js.Promise.reject(new js.Error("offline"))
    ): js.Function2[String, js.Dynamic, js.Promise[Nothing]]
    Browser.ajaxHead("/offline").map(result => assert(result.isLeft)).unsafeToFuture()
  }
  private def browser = Resource.make(IO {
    val env = new TestDOM("<div></div>", js.Dynamic.literal(url = "https://example.test/"))
    val globals = js.Dynamic.global.globalThis
    val names = List("window", "document", "FileReader", "Blob", "Image")
    val previous = names.map(name => name -> globals.selectDynamic(name))
    names.foreach(name => globals.updateDynamic(name)(if (name == "window") env.window else env.window.selectDynamic(name)))
    (env, previous)
  }) { case (env, previous) => IO {
    previous.foreach { case (name, value) => js.Dynamic.global.globalThis.updateDynamic(name)(value) }
    env.window.close()
  }}

  test("DOM and storage effects are lazy and compose before one notification") {
    browser.use { _ =>
      val operation = for {
        _ <- Browser.replaceUrl("/draft")
        _ <- Browser.setHtmlTheme("dark")
        _ <- Browser.setLocalStorage("draft", "saved")
        value <- Browser.getLocalStorage("draft")
      } yield value
      val command = operation.toCmd
      assert(dom.window.location.pathname == "/")
      assert(dom.window.localStorage.getItem("draft") == null)
      assert(dom.document.documentElement.getAttribute("data-theme") == null)
      command.io.map { result =>
        assert(result.contains(Some("saved")))
        assert(dom.window.location.pathname == "/draft")
        assert(dom.document.documentElement.getAttribute("data-theme") == "dark")
      }
    }.unsafeToFuture()
  }

  test("GET is lazy and preserves parsing, decoding and network errors") {
    var calls = 0
    var body = "42"
    js.Dynamic.global.globalThis.fetch = ((_: String) => {
      calls += 1
      js.Promise.resolve(js.Dynamic.literal(
        text = (() => js.Promise.resolve(body)): js.Function0[js.Promise[String]]
      ))
    }): js.Function1[String, js.Promise[js.Dynamic]]
    val json = Browser.ajaxGetJson("/value")
    val decoded = Browser.ajaxGet("/value", Decoder.decodeInt)
    assert(calls == 0)
    (for {
      a <- json
      b <- decoded
      _ <- IO { body = "invalid json" }
      c <- json
      _ <- IO { body = "\"wrong type\"" }
      d <- decoded
      _ <- IO {
        js.Dynamic.global.globalThis.fetch = ((_: String) =>
          js.Promise.reject(new js.Error("offline"))
        ): js.Function1[String, js.Promise[Nothing]]
      }
      e <- json
      f <- decoded
    } yield {
      assert(a.toOption.flatMap(_.asNumber.flatMap(_.toInt)).contains(42))
      assert(b == Right(42))
      assert(c.isLeft && d.isLeft && e.isLeft && f.isLeft)
    }).unsafeToFuture()
  }

  test("FileReader handles success and failure and Base64 decoding is suspended") {
    browser.use { env =>
      var reads = 0
      var fail = false
      val error = js.Dynamic.literal().asInstanceOf[dom.ProgressEvent]
      val prototype = env._1.window.FileReader.prototype
      prototype.readAsDataURL = ((reader: js.Dynamic, _: js.Any) => {
        reads += 1
        if (fail) reader.onerror(error)
        else {
          js.Dynamic.global.Object.defineProperty(reader, "result",
            js.Dynamic.literal(value = "data:text/plain;base64,YQ=="))
          reader.onload(js.Dynamic.literal())
        }
        ()
      }): js.ThisFunction1[js.Dynamic, js.Any, Unit]
      val blob = new dom.Blob(js.Array("a"))
      val encoded = Browser.encodeAsBase64(blob)
      val trimmed = Browser.encodeAsBase64(blob, removePadding = true)
      val invalid = Browser.decodeBase64("%%%", "text/plain")
      assert(reads == 0)
      for {
        full <- encoded
        short <- trimmed
        _ <- IO { fail = true }
        failed <- encoded
        decoded <- Browser.decodeBase64("YQ==", "text/plain")
        bad <- invalid.attempt
      } yield {
        assert(full == Right("YQ==") && short == Right("YQ"))
        assert(failed == Left(error) && bad.isLeft)
        assert(decoded.size == 1 && decoded.`type` == "text/plain")
      }
    }.timeout(2.seconds).unsafeToFuture()
  }

  test("geolocation registration is lazy and preserves both callback outcomes") {
    var calls = 0
    var fail = false
    val position = js.Dynamic.literal().asInstanceOf[dom.Position]
    val error = js.Dynamic.literal(code = 1).asInstanceOf[dom.PositionError]
    js.Dynamic.global.globalThis.window = js.Dynamic.literal(navigator = js.Dynamic.literal(
      geolocation = js.Dynamic.literal(getCurrentPosition =
        ((success: js.Function1[dom.Position, Unit], failure: js.Function1[dom.PositionError, Unit]) => {
          calls += 1
          if (fail) failure(error) else success(position)
        }): js.Function2[js.Function1[dom.Position, Unit], js.Function1[dom.PositionError, Unit], Unit]
      )
    ))
    val operation = Browser.getCurrentPosition
    assert(calls == 0)
    (for {
      good <- operation
      _ <- IO { fail = true }
      bad <- operation
    } yield assert(good == Right(position) && bad == Left(error) && calls == 2))
      .unsafeToFuture()
  }

  test("image loading starts only on execution and reports load errors") {
    browser.use { env =>
      var loads = 0
      var fail = false
      val prototype = env._1.window.HTMLImageElement.prototype
      js.Dynamic.global.Object.defineProperty(prototype, "src", js.Dynamic.literal(
        configurable = true,
        set = ((image: js.Dynamic, _: String) => {
          loads += 1
          val event = js.Dynamic.newInstance(env._1.window.Event)(if (fail) "error" else "load")
          image.dispatchEvent(event)
          ()
        }): js.ThisFunction1[js.Dynamic, String, Unit]
      ))
      val operation = Browser.createImage("/image.png")
      assert(loads == 0)
      for {
        image <- operation
        _ <- IO { fail = true }
        failed <- operation.attempt
      } yield {
        assert(loads == 2 && image.getAttribute("crossOrigin") == "anonymous")
        assert(failed.left.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
      }
    }.timeout(2.seconds).unsafeToFuture()
  }

}
