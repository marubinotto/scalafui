# Scalafui (Functional UI in Scala.js)

Scalafui is an experimental implementation of the Elm Architecture in Scala.js.

* [The Elm Architecture · An Introduction to Elm](https://guide.elm-lang.org/architecture/)
* [Functional UI \(Framework\-Free at Last\)](https://www.infoq.com/articles/functional-UI-introduction-no-framework/)

## Minimal Application Code

The following code is an example of a minimal application implementation using Scalafui. The framework is a single Scala file ([FunctionalUI.scala](src/main/scala/scalafui/FunctionalUI.scala)) that contains the necessary constructs to implement web frontend applications following the Elm Architecture.

```scala
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html._

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
* [examples/stopwatch/src/main/scala/scalafui/stopwatch/Main.scala](examples/stopwatch/src/main/scala/scalafui/stopwatch/Main.scala)

```console
$ cd examples/stopwatch
$ yarn
$ yarn run dev
```

```console
sbt> ~exampleStopwatch/fastLinkJS
```
