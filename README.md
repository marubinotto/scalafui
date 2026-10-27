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
        ).use(_ => IO.never)
      }
    }
}
```


## Running Examples

* Requirements
    * [sbt](https://www.scala-sbt.org/)
    * [Node.js](https://nodejs.org/en/download/releases) 22.22.2+ (22.x), 24.15.0+ (24.x), or 26+ for the repository root and tests
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

### fuidungeon

A small turn-based dungeon RPG with randomly generated rooms and corridors and
an automatically following camera. Carry your health between floors and see how
deep you can descend. Demonstrates pure game rules, immutable
state, and random initialization through `Cmd`.

See the [fuidungeon README](examples/fuidungeon/README.md) for setup, controls,
game rules, and development instructions.

<img alt="screenshot" src="examples/fuidungeon/screenshot.png" width="30%">

## Browser effects

Browser operations return lazy `IO` values. Compose them first, then choose how
to notify the application with `.toCmd`, `Cmd.exec`, or `Cmd.optional`.

```scala
import fui.*
import scala.concurrent.duration.*

val loadDraft = (
  Browser.setLocalStorage("draft", "hello") *>
    Browser.getLocalStorage("draft")
).toCmd // emits Some("hello") as the message

val saveOnly = Cmd.exec(Browser.setLocalStorage("draft", "hello"))
val request = Browser.ajaxGetJson("/data.json").toCmd.debounce("load", 500.millis)
```

URL changes, reload, theme changes, and storage writes/removal return `IO[Unit]`.
`getLocalStorage` returns `IO[Option[String]]`, with None for a missing key.
HTTP, FileReader encoding, and geolocation retain their existing `Either` result
types inside IO. `ajaxHead` returns the response even for a non-success HTTP status;
inspect its status yourself.

`createImage` now returns `IO[HTMLImageElement]` instead of Future, and
`decodeBase64` returns `IO[Blob]`. Image loading and decoding begin only when
the IO runs; loading or decoding failures fail the IO. Existing cancellation
behavior is unchanged.

These return-type changes are breaking: remove Browser calls' `.io` accessors
and obsolete `Msg` type arguments. Convert result-producing operations with
`.map(Message.apply).toCmd`; wrap notification-free operations in `Cmd.exec`.
Calling `.toCmd` on `IO[Unit]` emits Unit, so it is not a replacement for
`Cmd.exec`. Apply command debounce after the conversion.

`runProgram` remains a Resource, callback debounce helpers retain their
signatures, and `Browser.send` remains available for compatibility.

## Command composition

Compose computations with ordinary `IO`, then use `.toCmd` (available through
`import fui.*`) to emit the result as a message. No implicit conversion is used.

```scala
import cats.effect.IO
import fui.*
import scala.concurrent.duration.*

def search(query: String): IO[String] = IO.pure(query)
def saveDraft: IO[Unit] = IO.unit
case class SearchCompleted(result: String)

// Sequence effects before converting to a command.
val operation = saveDraft *> search("scala")
val command = operation.map(SearchCompleted.apply).toCmd

// Run without a message, or emit only when a result is present.
val save = Cmd.exec(saveDraft)
val optional = Cmd.optional(search("scala").map(result =>
  Option.when(result.nonEmpty)(SearchCompleted(result))
))

// Independent commands: save immediately, debounce the whole search computation.
val commands = Cmd.Batch(
  search("scala").map(SearchCompleted.apply).toCmd.debounce("search", 500.millis),
  save
)
```

`IO[Option[A]].toCmd` emits the Option itself (including None), and
`IO[Unit].toCmd` emits Unit. Use `Cmd.optional` or `Cmd.exec` to suppress messages.
All these constructors are lazy and preserve IO errors and cancellation.

Use IO `flatMap` for dependent steps and Cats `sequence` / `parSequence` for
sequential / parallel computations whose results should be collected before
emitting one message. Batch commands instead emit independently as they finish;
completion order is not guaranteed.

Debounce applies only to a single command, with delays in milliseconds. It replaces
pending commands with the same key but does not cancel work that has already
started. `map` and `toNone` preserve that policy. `++` and Batch append/prepend
operations remain available, and `Cmd.none` is an empty Batch.

Migration: `Cmd.One.flatMap` and `Cmd.One.sequenceOf` have been removed; compose
the original IO computations before attaching command policies. Do not extract
`.io` from a debounced command to compose it, since this discards its policy.
`Cmd.none` now returns `Cmd.Batch`, not `Cmd.One`. `Cmd(io)`, direct
`Cmd.One` construction and `Cmd.One.pure` remain supported.

## Routing

Scalafui uses Scala 3 and includes `fui.router`, which is a modified version of [trail](https://github.com/sparsetech/trail).

```scala
import fui.router._

val search = Root & Param[String]("query")
val work = Root / "work" / Arg[String]()
val paged = work & Param[String]("query") & Param[Int]("page")

search.url("Scala + UI") // Right("/?query=Scala+%2B+UI")
paged.url(("123", "Scala", 2)) // Right("/work/123?query=Scala&page=2")
```

Patterns match relative URLs containing a path, query and optional fragment. Required query parameters must be present and decode successfully; empty strings are valid. The first repeated value wins, unknown parameters and fragments are ignored, and path-only routes also match URLs with queries. Order more specific query patterns before path-only fallbacks. Both path arguments and query values use `Codec`; query encoding follows `URLSearchParams`.

## Rendering

Scalafui uses React 18 and creates one React root per Runtime. Keep `react` and `react-dom` aligned at version 18.3.1 in applications.

## Application lifetime

`Runtime.make(container, program, dispatcher)` returns `Resource[IO, Runtime[Model, Msg]]`, and `Browser.runProgram` returns `Resource[IO, Unit]`. Use `Browser.runProgram(...).use(_ => IO.never)` inside `Dispatcher.parallel[IO].use`. Leaving the resource scope stops message processing, cancels commands and subscriptions (waiting for their finalizers), clears debounce timers, removes URL listeners, and unmounts React. Partial startup failures also release acquired resources. Dispatch after release is ignored. The caller owns the Dispatcher; releasing one Runtime does not stop other Runtimes sharing it. Cancellation of browser operations such as fetch still depends on the individual command adapter.

Messages are queued: `dispatch` schedules an update rather than updating the model synchronously. The initial view and subscriptions are set up before the initial command runs. Commands remain independently scheduled; the message queue does not guarantee command completion order.

## Tests

Run `yarn install` at the repository root, then `sbt test`. Runtime tests use jsdom 30 and require the Node.js versions listed above; browser command tests stub storage and fetch. Tests run serially because they install temporary browser globals.

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

## Typed time and URL generation

`Sub.every` emits Unix epoch milliseconds obtained through `Clock[IO].realTime`.
Its interval and `Cmd.One.debounce` delays use `FiniteDuration`:

```scala
import scala.concurrent.duration.*

request.toCmd.debounce("search", 500.millis)
```

Debounce replaces only pending work with the same key. Commands already started
continue independently. Zero or negative delays do not wait. Pending waits use
`IO.sleep` and are canceled when the Runtime resource is released.
The old numeric-millisecond debounce API and both `Browser.debounce` helpers
have been removed; use command debounce instead.

`RoutePattern.url(value)` now returns `Either[RouteError, String]`.
`PathArgumentRejected` and `QueryParameterRejected(name)` report a Codec returning
`None`; `EncodingFailed(underlying)` preserves an exception from encoding.
Handle the result explicitly, or lift it into IO before browser navigation:

```scala
Cmd.exec(IO.fromEither(route.url(value)).flatMap(url => Browser.pushUrl(url)))
```

The previous String-returning URL API is no longer available. URL parsing keeps
its existing `Option` result and matching behavior.
