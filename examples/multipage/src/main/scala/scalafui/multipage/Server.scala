package scalafui.multipage

import scala.util.Success
import scala.util.Failure

import io.circe._
import io.circe.Decoder
import io.circe.parser._
import io.circe.syntax._

import scala.scalajs.js.URIUtils
import org.scalajs.dom.ext.Ajax

import scalafui.FunctionalUI._
import scalafui.multipage.Domain.Work
import scalafui.multipage.Domain.Edition

//
// Open Library API - https://openlibrary.org/developers/api
//
object Server {

  //
  // Work
  //

  val workDecoder: Decoder[Work] =
    Decoder.forProduct4("key", "title", "author_name", "description")(
      (key: String, title, author_name, description) =>
        Work(key.stripPrefix("/works/"), title, author_name, description)
    )

  val worksDecoder = new Decoder[Seq[Work]] {
    implicit val workSeqDecoder = Decoder.decodeSeq(workDecoder)
    final def apply(c: HCursor): Decoder.Result[Seq[Work]] =
      for {
        works <- c.downField("docs").as[Seq[Work]]
      } yield works
  }

  def searchWorks[Msg](
      query: String,
      createMsg: Either[Throwable, Seq[Work]] => Msg
  ): Cmd[Msg] = {
    val encodedQuery = URIUtils.encodeURIComponent(query)
    Browser.ajaxGet(
      "https://openlibrary.org/search.json?q=" + encodedQuery,
      worksDecoder,
      createMsg
    )
  }

  def fetchWork[Msg](
      id: String,
      createMsg: Either[Throwable, Work] => Msg
  ): Cmd[Msg] = {
    Browser.ajaxGet(
      "https://openlibrary.org/works/" + id + ".json",
      workDecoder,
      createMsg
    )
  }

  //
  // Edition
  //

  val editionDecoder: Decoder[Edition] =
    Decoder.forProduct5("key", "title", "publish_date", "publishers", "covers")(
      (key: String, title, publish_date, publishers, covers) =>
        Edition(
          key.stripPrefix("/books/"),
          title,
          publish_date,
          publishers,
          covers
        )
    )

  val editionsDecoder = new Decoder[Seq[Edition]] {
    implicit val editionSeqDecoder = Decoder.decodeSeq(editionDecoder)
    final def apply(c: HCursor): Decoder.Result[Seq[Edition]] =
      for {
        editions <- c.downField("entries").as[Seq[Edition]]
      } yield editions
  }

  def fetchEditions[Msg](
      workId: String,
      createMsg: Either[Throwable, Seq[Edition]] => Msg
  ): Cmd[Msg] = {
    val encodedWorkId = URIUtils.encodeURIComponent(workId)
    Browser.ajaxGet(
      "https://openlibrary.org/works/" + encodedWorkId + "/editions.json",
      editionsDecoder,
      createMsg
    )
  }
}
