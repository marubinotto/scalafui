package fui

import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import cats.effect.{Deferred, IO, Resource}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import slinky.web.html.div

@js.native
@JSImport("jsdom", "JSDOM")
class TestDOM(@scala.annotation.unused html: String, @scala.annotation.unused options: js.Object) extends js.Object {
  val window: js.Dynamic = js.native
}

class RuntimeSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def environment = Resource.make(IO {
    val previousWindow = js.Dynamic.global.globalThis.window
    val previousDocument = js.Dynamic.global.globalThis.document
    val env = new TestDOM("<div id='app'></div>", js.Dynamic.literal(url = "https://example.test/"))
    js.Dynamic.global.globalThis.window = env.window
    js.Dynamic.global.globalThis.document = env.window.document
    (env, previousWindow, previousDocument)
  }) { case (env, previousWindow, previousDocument) =>
    IO.sleep(20.millis) *> IO {
      env.window.close()
      js.Dynamic.global.globalThis.window = previousWindow
      js.Dynamic.global.globalThis.document = previousDocument
    }
  }

  test("reentrant dispatch is queued and updates are processed without nesting") {
    environment.use { _ => Dispatcher.parallel[IO].use { dispatcher =>
      for {
        done <- Deferred[IO, Unit]
        events = scala.collection.mutable.ArrayBuffer.empty[String]
        runtimeRef = new Array[Runtime[Int, Int]](1)
        runtime <- Runtime.make(dom.document.getElementById("app"), Program[Int, Int](
          _ => (0, Cmd.none),
          (model, _) => div(model.toString),
          (msg, model) => {
            events += s"start-$msg-$model"
            if (msg == 1) runtimeRef(0).dispatch(2)
            events += s"end-$msg"
            (model + msg, if (msg == 2) Cmd(done.complete(()).as(None)) else Cmd.none)
          }
        ), dispatcher)
        _ <- IO { runtimeRef(0) = runtime; runtime.dispatch(1) }
        _ <- done.get.timeout(2.seconds)
      } yield assert(events.toList == List("start-1-0", "end-1", "start-2-1", "end-2"))
    }}.unsafeToFuture()
  }

  test("initial view and subscription evaluation precede the initial command") {
    environment.use { _ => Dispatcher.parallel[IO].use { dispatcher =>
      for {
        done <- Deferred[IO, Unit]
        events = scala.collection.mutable.ArrayBuffer.empty[String]
        _ <- Runtime.make(dom.document.getElementById("app"), Program[Int, Int](
          _ => (7, Cmd(IO { events += "command"; Some(1) })),
          (model, _) => { events += s"view-$model"; div(model.toString) },
          (_, model) => (model + 1, Cmd(done.complete(()).as(None))),
          _ => { events += "subscriptions"; Sub.Empty }
        ), dispatcher)
        _ <- done.get.timeout(2.seconds)
      } yield assert(events.take(4).toList == List("view-7", "subscriptions", "command", "view-8"))
    }}.unsafeToFuture()
  }

  test("a burst of messages is retained and debounced commands keep only the last value") {
    environment.use { _ => Dispatcher.parallel[IO].use { dispatcher =>
      for {
        done <- Deferred[IO, (Int, Int)]
        runtime <- Runtime.make(dom.document.getElementById("app"), Program[Int, Int](
          _ => (0, Cmd.none),
          (model, _) => div(model.toString),
          (msg, model) =>
            if (msg < 0) (model, Cmd(done.complete((model, msg)).as(None)))
            else (model + 1, Browser.send(-msg).debounce("burst", 30))
        ), dispatcher)
        _ <- IO((1 to 100).foreach(runtime.dispatch))
        count <- done.get.timeout(3.seconds)
      } yield assert(count == (100, -100))
    }}.unsafeToFuture()
  }

  test("pushUrl and popstate dispatch URL changes") {
    environment.use { env => Dispatcher.parallel[IO].use { dispatcher =>
      for {
        pushed <- Deferred[IO, String]
        popped <- Deferred[IO, String]
        runtime <- Runtime.make(dom.document.getElementById("app"), Program[Int, String](
          _ => (0, Cmd.none),
          (model, _) => div(model.toString),
          (msg, model) => (model + 1, Cmd((if (model == 0) pushed else popped).complete(msg).as(None))),
          onUrlChange = Some(_.pathname)
        ), dispatcher)
        _ <- IO(runtime.onPushUrl(new dom.URL("https://example.test/pushed")))
        first <- pushed.get.timeout(2.seconds)
        _ <- IO {
          dom.window.history.pushState(null, "", "/popped")
          env._1.window.dispatchEvent(js.Dynamic.newInstance(env._1.window.PopStateEvent)("popstate"))
        }
        second <- popped.get.timeout(2.seconds)
      } yield assert(first == "/pushed" && second == "/popped")
    }}.unsafeToFuture()
  }
}
