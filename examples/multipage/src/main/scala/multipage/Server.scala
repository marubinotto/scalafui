package multipage

import io.circe._
import io.circe.Decoder

import scala.scalajs.js.URIUtils

import fui._

import multipage.domain.Work
import multipage.domain.Edition

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
    implicit val workSeqDecoder: Decoder[Seq[Work]] =
      Decoder.decodeSeq(workDecoder)
    final def apply(c: HCursor): Decoder.Result[Seq[Work]] =
      for {
        works <- c.downField("docs").as[Seq[Work]]
      } yield works
  }

  def searchWorks(query: String): Cmd.One[Either[Throwable, Seq[Work]]] = {
    val encodedQuery = URIUtils.encodeURIComponent(query)
    Browser.ajaxGet(
      "https://openlibrary.org/search.json?q=" + encodedQuery,
      worksDecoder
    )
  }

  def fetchWork(id: String): Cmd.One[Either[Throwable, Work]] = {
    Browser.ajaxGet(
      "https://openlibrary.org/works/" + id + ".json",
      workDecoder
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
    implicit val editionSeqDecoder: Decoder[Seq[Edition]] =
      Decoder.decodeSeq(editionDecoder)
    final def apply(c: HCursor): Decoder.Result[Seq[Edition]] =
      for {
        editions <- c.downField("entries").as[Seq[Edition]]
      } yield editions
  }

  def fetchEditions(
      workId: String
  ): Cmd.One[Either[Throwable, Seq[Edition]]] = {
    val encodedWorkId = URIUtils.encodeURIComponent(workId)
    Browser.ajaxGet(
      "https://openlibrary.org/works/" + encodedWorkId + "/editions.json",
      editionsDecoder
    )
  }
}
