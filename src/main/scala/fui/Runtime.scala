package fui

import scala.collection.mutable.{Map => MutableMap}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.Event

import cats.effect.IO
import cats.effect.std.Dispatcher
import scala.compiletime.uninitialized

import slinky.web.ReactDOMClient

class Runtime[Model, Msg] private (
    container: Element,
    val program: Program[Model, Msg],
    dispatcher: Dispatcher[IO]
) {
  private val reactRoot = ReactDOMClient.createRoot(container)
  private var state: Model = uninitialized
  private val subs: MutableMap[String, Option[Sub.Unsubscribe]] = MutableMap()
  private val debounceTimers: MutableMap[String, Int] = MutableMap()

  def dispatch(msg: Msg): Unit = apply(program.update(msg, state))

  private def apply(change: (Model, Cmd[Msg])): Unit = {
    val (model, cmd) = change
    state = model

    reactRoot.render(program.view(model, dispatch))
    run(cmd)
    updateSubs(state)
  }

  private def run(cmd: Cmd[Msg]): Unit =
    cmd match {
      case cmd @ Cmd.One(_, Some(Cmd.Debounce(key, delay))) => {
        debounceTimers.get(key).foreach(dom.window.clearTimeout)
        val timer = dom.window.setTimeout(() => runOne(cmd), delay)
        debounceTimers.put(key, timer)
      }
      case cmd: Cmd.One[Msg]          => runOne(cmd)
      case Cmd.Batch(cmds @ _*)       => for (cmd <- cmds) run(cmd)
    }

  private def runOne(cmd: Cmd.One[Msg]): Unit =
    dispatcher.unsafeRunAndForget(
      cmd.io.flatMap(optionMsg => IO(optionMsg.foreach(dispatch)))
    )

  private def updateSubs(model: Model): Unit = {
    val nextSubs = Sub.toMap(program.subscriptions(model))
    val keysToAdd = nextSubs.keySet.diff(subs.keySet)
    val keysToRemove = subs.keySet.diff(nextSubs.keySet)
    keysToAdd.foreach(key => {
      // Register the key first to avoid multiple subscriptions
      subs.update(key, None)

      // subscribe and keep the `unsubscribe` function
      nextSubs.get(key) match {
        case Some(subscribe) => subscribe(dispatch, subs.update(key, _))
        case None            => subs.update(key, None)
      }
    })
    keysToRemove.foreach(key => {
      // remove and unsubscribe
      subs.remove(key).map(_.map(_()))
    })
  }

  def onPushUrl(url: URL): Unit =
    program.onUrlChange.map(_(url)).map(dispatch)

}

object Runtime {
  def make[Model, Msg](
      container: Element,
      program: Program[Model, Msg],
      dispatcher: Dispatcher[IO]
  ): IO[Runtime[Model, Msg]] = IO {
    val runtime = new Runtime(container, program, dispatcher)
    val init = program.init(new URL(dom.window.location.href))
    program.onUrlChange.foreach(onUrlChange =>
      dom.window.addEventListener(
        "popstate",
        (_: Event) => runtime.dispatch(onUrlChange(new URL(dom.window.location.href)))
      )
    )
    runtime.apply(init)
    runtime
  }
}
