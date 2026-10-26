# Scalafui (Functional UI in Scala.js)

Scalafui is an experimental implementation of the Elm Architecture in Scala.js.

* [The Elm Architecture · An Introduction to Elm](https://guide.elm-lang.org/architecture/)
* [Functional UI \(Framework\-Free at Last\)](https://www.infoq.com/articles/functional-UI-introduction-no-framework/)

## Minimal Application Code

The following code is an example of a minimal application implementation using Scalafui.

```scala
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import fui._

object Main {

  //
  // MODEL
  //

  case class Model(messages: Seq[String] = Seq.empty, input: String = "")

  def init(url: URL): (Model, Cmd[Msg]) = (Model(), Cmd.none)

  //
  // UPDATE
  //

  sealed trait Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case Input(input) =>
        (
          model.modify(_.input).setTo(input),
          Cmd.none
        )

      case Send =>
        (
          model
            .modify(_.messages).using(_ :+ model.input)
            .modify(_.input).setTo(""),
          Cmd.none
        )
    }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    div(
      h1("Welcome to Scalafui!"),
      form(className := "message-input")(
        input(
          value := model.input,
          onInput := (e => dispatch(Input(e.target.value)))
        ),
        button(
          `type` := "submit",
          onClick := (e => {
            e.preventDefault()
            dispatch(Send)
          })
        )("Send")
      ),
      div(className := "messages")(
        model.messages.map(div(className := "message")(_))
      )
    )

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
          Program(init, view, update),
          dispatcher
        )
      } *> IO.never
    }
}
```


## Running Examples

* Requirements
    * [sbt](https://www.scala-sbt.org/)
    * [Node.js](https://nodejs.org/en/download/releases) 20.19+ (20.x) or 22.12+
    * [Yarn](https://yarnpkg.com/) Classic 1.22.x

### Hello World

* [src/main/scala/Main.scala](src/main/scala/Main.scala)

In development mode, use two terminals in parallel:

```console
$ yarn
$ yarn run dev
```

```console
sbt> ~fastLinkJS
```

### ToDo

* Scalafui's version of [TodoMVC](https://todomvc.com/)
* [examples/todo/src/main/scala/todo/Main.scala](examples/todo/src/main/scala/todo/Main.scala)

```console
$ cd examples/todo
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleTodo/fastLinkJS
```

### Multipage

* Multi-page application with [Ajax calls](examples/multipage/src/main/scala/multipage/Server.scala)
* Debounce Search Cmd 
* [examples/multipage/src/main/scala/multipage/Main.scala](examples/multipage/src/main/scala/multipage/Main.scala)

```console
$ cd examples/multipage
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleMultipage/fastLinkJS
```

### Stopwatch

* An example of subscription which allows us to listen to external events
* [examples/stopwatch/src/main/scala/stopwatch/Main.scala](examples/stopwatch/src/main/scala/stopwatch/Main.scala)

```console
$ cd examples/stopwatch
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleStopwatch/fastLinkJS
```

## Browser commands

`Browser.ajaxHead(url)` returns an `Either[Throwable, dom.Response]`; inspect the response status in your update function.
`Browser.setLocalStorage(key, value)` and `removeLocalStorage(key)` perform storage operations without emitting a message.
`Browser.getLocalStorage(key)` emits `Option[String]`, including `None` for a missing key.

## Command composition

Use `Cmd.Batch` for independent commands, `Cmd.One.flatMap` to chain commands that emit a message, or `Cmd.One.sequenceOf` to execute commands in order and collect their messages. `Cmd.Sequence` and `One.toBatch` have been removed.

## Routing

Scalafui uses Scala 3 and includes `fui.router`, adapted from Cotoami's router with query parameter support. The Multipage example no longer depends on Trail.

```scala
import fui.router._

val search = Root & Param[String]("query")
val work = Root / "work" / Arg[String]()
val paged = work & Param[String]("query") & Param[Int]("page")

search.url("Scala + UI") // /?query=Scala+%2B+UI
paged.url(("123", "Scala", 2)) // /work/123?query=Scala&page=2
```

Patterns match relative URLs containing a path, query and optional fragment. Required query parameters must be present and decode successfully; empty strings are valid. The first repeated value wins, unknown parameters and fragments are ignored, and path-only routes also match URLs with queries. Order more specific query patterns before path-only fallbacks. Both path arguments and query values use `Codec`; query encoding follows `URLSearchParams`.

## Rendering

Scalafui uses React 18 and creates one React root per Runtime. Keep `react` and `react-dom` aligned at version 18.3.1 in applications.

## Application lifetime

`Browser.runProgram(container, program, dispatcher)` now returns `IO[Unit]`; execute it inside a long-lived `Dispatcher.parallel[IO].use` scope. The examples keep that scope alive with `IO.never` and run the outer IO from a standard `main` method. Runtime construction is effectful through `Runtime.make`; direct construction is no longer supported.

Messages are queued: `dispatch` schedules an update rather than updating the model synchronously. The initial view and subscriptions are set up before the initial command runs. Commands remain independently scheduled; the message queue does not guarantee command completion order.

## Tests

Run `yarn install` at the repository root, then `sbt test`. Runtime tests use jsdom; browser command tests stub storage and fetch. Tests run serially because they install temporary browser globals.

## Subscriptions

`Sub.Impl(id, stream)` now uses `fs2.Stream[IO, Msg]`. `Sub.timeout`, `Sub.every` and `Sub.fromEvent` retain their call signatures. Use `Sub.fromCallback` for external APIs: the registration IO returns an IO that unregisters the callback.

```scala
Sub.fromCallback[Msg]("external-events") { emit =>
  IO {
    val unregister = registerListener(emit)
    IO(unregister())
  }
}
```

Runtime cancels a subscription's stream when its ID disappears, running its cleanup. IDs determine identity: changing a stream under an existing ID does not restart it. Remove the ID before adding it again to restart a completed or stopped subscription. Callback adapters must return their cleanup after registration; registration failures should release any partially acquired external resources themselves.
