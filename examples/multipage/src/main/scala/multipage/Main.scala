package multipage

import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL
import slinky.core.facade.ReactElement
import slinky.hot
import slinky.web.html.*
import marubinotto.fui.*
import marubinotto.router.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global

object Main {
  object Route {
    val search = Root
    val searchWithQuery = Root & Param[String]("query")
    val work = Root / "work" / Arg[String]()
  }

  object Pages extends ChildFeatures {
    val search = child(page.Search)
    val work = child(page.Work)
  }

  case class Model(page: Pages.State = Pages.empty)

  enum Msg {
    case UrlChanged(url: URL)
    case PageEvent(event: Pages.Event)
  }

  val pageBinding = Pages.bind[Model, Msg, Nothing](
    policy = Pages.policy(Pages.exclusive(Pages.search, Pages.work)),
    state = _.page,
    replace = (model, value) => model.copy(page = value),
    wrap = Msg.PageEvent.apply,
    extract = {
      case Msg.PageEvent(event) => Some(event)
      case _                    => None
    },
    onOutput = (output, model) =>
      output match {
        case Pages.search.output(page.Search.Out.QueryChanged(query)) =>
          val url =
            if (query.isBlank) Route.search.url(())
            else Route.searchWithQuery.url(query)
          Transition(
            model,
            Cmd.exec(IO.fromEither(url).flatMap(Browser.replaceUrl))
          )
        case Pages.search.output(page.Search.Out.OpenWork(id)) =>
          Transition(
            model,
            Cmd.exec(
              IO.fromEither(Route.work.url(id)).flatMap(url =>
                Browser.pushUrl(url)
              )
            )
          )
        case _ => Transition.unchanged(model)
      }
  )

  def init(url: URL): Transition[Model, Msg, Nothing] =
    applyUrlChange(url, Model())

  def applyUrlChange(url: URL, model: Model): Transition[Model, Msg, Nothing] =
    (url.pathname + url.search + url.hash) match {
      case Route.searchWithQuery(query) =>
        pageBinding.open(model, Pages.search, query)
      case Route.search(_) => pageBinding.open(model, Pages.search, "")
      case Route.work(id)  => pageBinding.open(model, Pages.work, id)
      case _               => pageBinding.clear(model)
    }

  private def update(msg: Msg, model: Model): Transition[Model, Msg, Nothing] =
    msg match {
      case Msg.UrlChanged(url) => applyUrlChange(url, model)
      case Msg.PageEvent(_)    => Transition.unchanged(model)
    }

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    div(className := "app")(
      div(className := "app-header")(),
      if (Pages.search.model(model.page).isDefined)
        pageBinding.view(model, Pages.search, dispatch, div())
      else
        pageBinding.view(
          model,
          Pages.work,
          dispatch,
          div(className := "page-not-found")("Page Not Found")
        )
    )

  val program = FeatureProgram(
    init = init,
    update = update,
    view = view,
    onUrlChange = Some(Msg.UrlChanged.apply)
  ).withFeatures(pageBinding).program

  def main(args: Array[String]): Unit = run.unsafeRunAndForget()

  def run: IO[Unit] =
    IO { if (LinkingInfo.developmentMode) hot.initialize() } *>
      Browser.run(dom.document.getElementById("app"), program)
}
