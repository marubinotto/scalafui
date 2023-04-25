# Scalafui (Functional UI in Scala.js)

Scalafui is an experimental implementation of the Elm Architecture in Scala.js.

* [The Elm Architecture · An Introduction to Elm](https://guide.elm-lang.org/architecture/)
* [Functional UI \(Framework\-Free at Last\)](https://www.infoq.com/articles/functional-UI-introduction-no-framework/)

## Minimal Application Code

The following code is an example of a minimal application implementation using Scalafui. The framework is a single Scala file ([FunctionalUI.scala](src/main/scala/scalafui/FunctionalUI.scala)) that contains the necessary constructs to implement web frontend applications following the Elm Architecture.

```scala
...
import scalafui.FunctionalUI._

object Main {

  //
  // MODEL
  //

  case class Model(messages: Seq[String], input: String)

  def init(url: URL): (Model, Cmds[Msg]) = (Model(Seq.empty, ""), Seq.empty)

  //
  // UPDATE
  //

  sealed trait Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

  def update(msg: Msg, model: Model): (Model, Cmds[Msg]) =
    msg match {
      case Input(input) =>
        (model.copy(input = input), Seq.empty)

      case Send =>
        (
          model.copy(messages = model.messages :+ model.input, input = ""),
          Seq.empty
        )
    }

  //
  // VIEW
  //

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    div(
      h1("Welcome to Functional UI!"),
      div(className := "message-input")(
        input(
          value := model.input,
          onInput := (e => dispatch(Input(e.target.value)))
        ),
        button(onClick := (e => dispatch(Send)))("Send")
      ),
      div(className := "messages")(
        model.messages.map(div(className := "message")(_))
      )
    )

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update)
    )
  }
}
```


## Running Examples

* Requirements
    * [sbt](https://www.scala-sbt.org/)
    * [Node.js](https://nodejs.org/en/download/releases) v18 or later
    * [Yarn](https://yarnpkg.com/)

### Hello World

* [src/main/scala/scalafui/Main.scala](src/main/scala/scalafui/Main.scala)

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
* [examples/todo/src/main/scala/scalafui/todo/Main.scala](examples/todo/src/main/scala/scalafui/todo/Main.scala)

```console
$ cd examples/todo
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleTodo/fastLinkJS
```

### Multipage

* Multi-page application with [Ajax calls](examples/multipage/src/main/scala/scalafui/multipage/Server.scala)
* [examples/multipage/src/main/scala/scalafui/multipage/Main.scala](examples/multipage/src/main/scala/scalafui/multipage/Main.scala)

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
* [examples/stopwatch/src/main/scala/scalafui/stopwatch/Main.scala](examples/stopwatch/src/main/scala/scalafui/stopwatch/Main.scala)

```console
$ cd examples/stopwatch
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleStopwatch/fastLinkJS
```
