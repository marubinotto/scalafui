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
        Browser.run(
          dom.document.getElementById("app"),
          Program(init, view, update),
          dispatcher
        )
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

<img alt="screenshot" src="examples/fuidungeon/screenshot.png" width="50%">

## Scalafui's basic concepts

Scalafui separates an application's definition from its execution. `Program`
defines how state changes and how it is displayed. `Runtime` holds the current
state and runs the application. `Browser` provides effects for interacting with
the browser, such as HTTP requests, storage, and navigation.

```mermaid
flowchart TD
  program["Program: defines init, update, view, subscriptions"]
  program -. "functions executed by" .-> runtime

  subgraph runtime["Runtime: manages state and execution"]
    init["init"] --> model["Model"]
    init --> cmd["Cmd: execute IO"]
    queue["Msg queue"] --> update["update"]
    model --> update
    update --> model
    update --> cmd
    model --> view["view"]
    model --> subs["subscriptions: manage active streams"]
    cmd -- "result message, if any" --> queue
    subs -- "messages" --> queue
  end

  view -- "React rendering" --> ui["UI"]
  ui -- "UI event / dispatch" --> queue
  cmd -- "browser effects" --> browser["Browser"]
  browser --> environment["Browser environment: HTTP, storage, history, events"]
  environment -- "external events" --> subs
```

Follow the loop from a UI event: a message enters the queue, `update` produces a
new Model, and `view` describes the resulting UI. Commands and subscriptions feed
messages back into the same loop. The nodes inside Runtime execute the functions
and effect descriptions supplied by Program; Program itself does not run them.
Browser effects are one source of command work; commands can use other `IO`
computations too.

### Program

`Program[Model, Msg]` brings together the functions that describe an application:

```scala
Program(
  init = init,
  view = view,
  update = update,
  subscriptions = subscriptions,
  onUrlChange = Some(UrlChanged.apply)
)
```

The names on the right refer to application-defined functions and a message
constructor. Only `init`, `view`, and `update` are required. Subscriptions default
to `Sub.Empty`, and URL change handling defaults to `None`.

#### Model/View/Update

- **Model** is the application's state, usually an immutable case class.
- **Msg** describes something that happened, such as input, a button click, or a
  completed request.
- **View** turns the current Model into a Slinky `ReactElement`. Event handlers
  use the supplied `dispatch: Msg => Unit` to send messages.
- **Update** receives a message and the current Model, and returns the next Model
  together with commands to execute.

The application supplies these function signatures:

```scala
def init(url: URL): (Model, Cmd[Msg])
def view(model: Model, dispatch: Msg => Unit): ReactElement
def update(msg: Msg, model: Model): (Model, Cmd[Msg])
```

`init` receives the current browser URL and supplies the initial state and startup
commands. Keep state transitions in `update` and describe side effects as commands.
Return `Cmd.none` when no effect is needed. The minimal application above shows
this loop using input and send messages.

#### Commands

A `Cmd[Msg]` describes work for Runtime to execute once, optionally returning a
message. For example, an update can return a loading state and a command that
fetches data; the command's result becomes a new message that updates the state.

Compose effects with Cats Effect `IO`, then choose how to notify the application:

- `.toCmd` emits the computed value as a message. It is available through
  `import fui.*`; there is no implicit conversion.
- `Cmd.exec(io)` runs the effect without emitting a message.
- `Cmd.optional(io)` emits a message only when the result is `Some(message)`.
- `Cmd.none` is an empty batch of commands.

```scala
import cats.effect.IO
import fui.*
import scala.concurrent.duration.*

case class SearchCompleted(result: String)

def search(query: String): IO[String] = IO.pure(query) // Example computation.
def saveDraft: IO[Unit] = IO.unit

val operation = saveDraft *> search("scala")
val command = operation.map(SearchCompleted.apply).toCmd
val save = Cmd.exec(saveDraft)
val optional = Cmd.optional(search("scala").map(result =>
  Option.when(result.nonEmpty)(SearchCompleted(result))
))

val commands = Cmd.Batch(
  search("scala").map(SearchCompleted.apply).toCmd.debounce("search", 500.millis),
  save
)
```

These constructors are lazy: creating a command does not execute its IO. They
preserve IO errors and cancellation. `.toCmd` on `IO[Option[A]]` emits the Option
itself, including `None`; on `IO[Unit]` it emits Unit. Use `Cmd.optional` or
`Cmd.exec` when those results should not produce messages. To turn an IO failure
into an application message, handle it in the IO before converting to a command,
for example with `attempt.map(ResultReceived.apply).toCmd`.

`Cmd.fromFuture(futureExpression)` takes its argument by name and evaluates it
on each command execution, emitting `Right(value)` on success or `Left(error)`
for a failed Future or a synchronous exception while creating it. Constructing
the command does not evaluate the expression. Passing an already-created Future
cannot defer its start; repeated executions then reuse that same Future.
Canceling the command does not cancel the underlying Future.

Use IO `flatMap` for dependent steps and Cats `sequence` / `parSequence` for
sequential / parallel computations whose results should be collected into one
message. `Cmd.Batch` runs commands independently, emitting results as they finish;
completion order is not guaranteed. Commands can also be combined with `++`, and
batches support append/prepend operations. `map` transforms emitted messages;
`toNone` on a single command suppresses its message.

Debounce applies to a single command and takes a `FiniteDuration`, such as
`500.millis`. A new command replaces pending work with the same key, but does not
cancel work that has already started. Zero or negative delays do not wait.
Compose the IO first, then attach debounce; extracting `.io` from a command loses
its debounce policy. `map` and `toNone` preserve the policy. Runtime cancels
pending debounce waits when it is released.

#### Subscriptions

A `Sub[Msg]` describes an external event source that Runtime should listen to
while it is needed. `subscriptions: Model => Sub[Msg]` selects those sources from
the current Model. Unlike a command, a subscription can keep producing messages
across many updates.

```scala
import fui.*
import scala.concurrent.duration.*

case class Model(running: Boolean)
case class Tick(epochMillis: Long)

def subscriptions(model: Model): Sub[Tick] =
  if (model.running) Sub.every(1.second, "clock").map(Tick.apply)
  else Sub.Empty
```

`Sub.every` emits Unix epoch milliseconds using `Clock[IO].realTime`.
`Sub.timeout(duration, msg, id)` emits once after a delay; `Sub.fromEvent` adapts
DOM events. Combine sources with `combine`, or use `Sub.Impl(id, stream)` for an
`fs2.Stream[IO, Msg]`.

For callback APIs, `Sub.fromResource` takes a registration function returning a
`Resource[IO, Unit]`. Registration starts when the subscription stream runs;
events emitted during registration are retained. The resource is released when
the stream ends, fails, or is canceled, before its callback Dispatcher is released.
Compose resources to safely register multiple sources:

```scala
import cats.effect.{IO, Resource}
import fui.*

def externalSources[Msg](
    registerFirst: (Msg => Unit) => (() => Unit),
    registerSecond: (Msg => Unit) => (() => Unit)
): Sub[Msg] =
  Sub.fromResource[Msg]("external-sources") { emit =>
    for {
      _ <- Resource.make(IO(registerFirst(emit)))(unregister => IO(unregister()))
      _ <- Resource.make(IO(registerSecond(emit)))(unregister => IO(unregister()))
    } yield ()
  }
```

If the second registration fails, the first is released. On normal teardown,
resources are released in reverse acquisition order, even if a finalizer fails.
Each acquisition must still clean up any partial work it performs before failing.

A single callback API returning an unregister function can be adapted with
`Resource.make`. Its acquisition is uncancelable: cancellation during registration
waits for acquisition to finish and then runs cleanup.

```scala
import cats.effect.{IO, Resource}
import fui.*

def externalEvents[Msg](register: (Msg => Unit) => (() => Unit)): Sub[Msg] =
  Sub.fromResource[Msg]("external-events") { emit =>
    Resource.make(IO(register(emit)))(unregister => IO(unregister())).map(_ => ())
  }
```

Runtime compares subscription IDs after each update. New IDs start streams;
removed IDs cancel streams and run their cleanup. Keeping an ID keeps its
existing stream, even if `subscriptions` constructs a different stream for that
ID. To restart a completed or stopped subscription, remove its ID in one update
before adding it again.

### Runtime

Runtime connects the pieces of Program: it owns the current Model, processes
messages, renders the view, starts commands, and maintains subscriptions.
`dispatch` queues a message rather than updating the Model synchronously.
Messages are processed one at a time, while commands execute independently;
queueing messages does not impose command completion order.

At startup, Runtime calls `init`, sets up the initial view and subscriptions,
and then starts the initial command. For each subsequent message, it calls
`update`, renders the returned Model, schedules the returned commands, and
reconciles subscriptions with that Model.

Scalafui uses React 18 and creates one React root per Runtime. Keep `react` and
`react-dom` aligned at version 18.3.1 in applications.

#### Application lifetime

Running an application acquires resources, including a React root, background
work, and event listeners. `Runtime.make(container, program, dispatcher)` returns
`Resource[IO, Runtime[Model, Msg]]`. The usual browser entry point,
`Browser.run`, returns `IO[Unit]` and monitors fatal update-loop failures.
`Browser.mount` returns `Resource[IO, Runtime[Model, Msg]]` for explicit lifetime
management. It starts the application on acquisition and unmounts it on release.

Keep the application's resource scope open for as long as the UI should run.
For an application-defined `program` and DOM `container`, the startup pattern is:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import fui.*

val run: IO[Unit] =
  Dispatcher.parallel[IO].use { dispatcher =>
    Browser.run(container, program, dispatcher)
  }
```

For a custom lifetime, `mount` exposes the Runtime. Given an
`applicationLifetime: IO[Unit]`, the caller can race it against fatal failure:

```scala
Dispatcher.parallel[IO].use { dispatcher =>
  Browser.mount(container, program, dispatcher).use { runtime =>
    IO.race(
      applicationLifetime,
      runtime.awaitFailure.flatMap(error => IO.raiseError[Unit](error))
    ).void
  }
}
```

The returned Runtime also exposes `dispatch(msg)` for sending messages.
`awaitFailure` only observes the cause; this example propagates it without
calling the error reporter. Use `Browser.run` for built-in fatal-error reporting.

The caller owns the Dispatcher and keeps it alive outside the application scope.
Multiple Runtimes can share it; releasing one does not stop the others.

Leaving the resource scope stops message processing, cancels commands and
subscriptions while waiting for their finalizers, clears debounce timers,
removes URL listeners, and unmounts React. Partial startup failures also release
acquired resources. Dispatch after release is ignored. Cancellation of `ajaxGetJson`, `ajaxGet`, and `ajaxHead` aborts the underlying
fetch request, including pending response body reads for GET. Cancellation of
`encodeAsBase64` aborts its FileReader and removes its event handlers. These
operations remain canceled rather than emitting a `Left` result. Each execution
owns its cancellation resources; canceling one does not affect another.
Image loading and geolocation do not currently abort their underlying operations.

#### Runtime errors

`Runtime.make`, `Browser.mount`, and `Browser.run` accept an optional
`onError: RuntimeError => IO[Unit]`, defaulting to console logging. Unhandled
command failures report `RuntimeError.Command`; failed subscriptions report
`RuntimeError.Subscription(id, cause)`. Other work continues. Failed subscriptions
keep their ID and do not automatically restart. Normal cancellation is not reported.
Use `.attempt` to turn expected application failures into messages as before.

An exception in the update loop stops message acceptance immediately.
`Runtime.awaitFailure` observes the first cause without reporting or releasing
resources. `Browser.run` waits for this failure, reports `RuntimeError.UpdateLoop`,
and raises the original exception, releasing the application resources.
Low-level resource users own their lifetime and fatal-error monitoring;
`Browser.mount` does not automatically interrupt its caller's `use` body.
Startup exceptions still fail resource acquisition directly.

Reporters must terminate and must not cancel themselves. Synchronous exceptions
and failed reporter IOs fall back to console logging without recursive notification.
Fatal failure recording and input shutdown precede reporting. React-internal
asynchronous rendering errors require a separate Error Boundary.

### Browser

`Browser.run` starts a Program in a DOM container and connects browser
navigation to its URL change handler. Cancel the returned IO to stop the application.

The other Browser helpers provide browser effects as lazy `IO` values. Compose
them first, then convert to commands when returning work from `init` or `update`:

```scala
import fui.*

case class DraftLoaded(value: Option[String])

val loadDraft = (
  Browser.setLocalStorage("draft", "hello") *>
    Browser.getLocalStorage("draft")
).map(DraftLoaded.apply).toCmd

val saveOnly = Cmd.exec(Browser.setLocalStorage("draft", "hello"))
```

The helpers cover these operations:

| Operations | Result inside IO |
| --- | --- |
| `pushUrl`, `replaceUrl`, `reload`, `setHtmlTheme` | `Unit` |
| `setLocalStorage`, `removeLocalStorage` | `Unit` |
| `getLocalStorage` | `Option[String]`; `None` for a missing key |
| `ajaxGetJson`, `ajaxGet`, `ajaxHead` | `Either` containing an error or the result |
| `encodeAsBase64`, `getCurrentPosition` | `Either` containing an API-specific error or the result |
| `decodeBase64` | `dom.Blob` |
| `createImage` | `dom.HTMLImageElement` |

HTTP, FileReader encoding, and geolocation report their asynchronous errors as
`Left` values, which can be mapped into messages. `ajaxHead` returns the response
even for a non-success HTTP status; inspect its status yourself. Image loading
and Base64 decoding begin only when the IO runs, and failures fail the IO.
`Browser.send(msg)` is also available to construct a command that emits a message.

## Routing

Routing connects the browser URL to application state, such as which page to
show. Scalafui includes `fui.router`, a modified version of
[trail](https://github.com/sparsetech/trail), for matching and generating URLs.
The application decides what each route means for its Model.

```scala
import fui.router.*

val search = Root & Param[String]("query")
val work = Root / "work" / Arg[String]()
val paged = work & Param[String]("query") & Param[Int]("page")

search.url("Scala + UI") // Right("/?query=Scala+%2B+UI")
paged.url(("123", "Scala", 2)) // Right("/work/123?query=Scala&page=2")
```

Use `Root` for the root path, `/` for literal segments or typed arguments, and
`& Param[A](name)` for query parameters. Patterns match relative URLs containing
a path, query, and optional fragment:

```scala
import org.scalajs.dom.URL

enum Page {
  case Search(query: String)
  case Work(id: String)
  case NotFound
}

def pageFromUrl(url: URL): Page =
  (url.pathname + url.search + url.hash) match {
    case search(query) => Page.Search(query)
    case Root(_)       => Page.Search("")
    case work(id)      => Page.Work(id)
    case _             => Page.NotFound
  }
```

Call this routing function from `init` to choose the initial page. Define a
message such as `UrlChanged(url: URL)`, pass `onUrlChange = Some(UrlChanged.apply)`
to Program, and handle that message in `update` by selecting the new page.
See the [Multipage example](examples/multipage/src/main/scala/multipage/Main.scala)
for a complete application with page-specific models and commands.

With `Browser.run` or `Browser.mount`, `Browser.pushUrl(url)` adds a history entry without
loading a new document and notifies the URL change handler. Browser back/forward
navigation also reaches that handler through `popstate`. `pushUrl(url, notify =
false)` suppresses its notification; `replaceUrl(url)` replaces the current
history entry without notifying the handler.

`RoutePattern.url(value)` returns `Either[RouteError, String]`. Handle errors
explicitly or lift the result into IO before navigating:

```scala
import cats.effect.IO
import fui.*

val openWork = Cmd.exec(
  IO.fromEither(work.url("123")).flatMap(url => Browser.pushUrl(url))
)
```

`PathArgumentRejected` and `QueryParameterRejected(name)` report a Codec returning
`None`; `EncodingFailed(underlying)` preserves an exception from encoding.
Lifting a `Left` into IO fails the command; map errors to application messages
instead if they should be displayed in the UI.

Parsing returns `Option` and supports pattern matching as above. Required query
parameters must be present and decode successfully; empty strings are valid.
The first repeated value wins, unknown parameters and fragments are ignored,
and path-only routes also match URLs with queries. Order more specific query
patterns before path-only fallbacks. Both path arguments and query values use
`Codec`; query encoding follows `URLSearchParams`.

## Tests

Run `yarn install` at the repository root, then `sbt test`. Runtime tests use
jsdom 30 and require the Node.js versions listed above; browser command tests
stub storage and fetch. Tests run serially because they install temporary
browser globals.
