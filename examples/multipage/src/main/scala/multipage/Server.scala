package multipage

import io.circe._

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
      (key: String, title: String, author_name: Option[List[String]], description: Option[String]) =>
        Work(key.stripPrefix("/works/"), title, author_name, description)
    )

  val worksDecoder = new Decoder[Seq[Work]] {
    given workSeqDecoder: Decoder[Seq[Work]] =
      Decoder.decodeSeq(using workDecoder)
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
      (key: String, title: String, publish_date: Option[String], publishers: Option[List[String]], covers: Option[List[Int]]) =>
        Edition(
          key.stripPrefix("/books/"),
          title,
          publish_date,
          publishers,
          covers
        )
    )

  val editionsDecoder = new Decoder[Seq[Edition]] {
    given editionSeqDecoder: Decoder[Seq[Edition]] =
      Decoder.decodeSeq(using editionDecoder)
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
