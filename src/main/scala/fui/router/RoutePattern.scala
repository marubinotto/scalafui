package fui.router

import scala.annotation.unused
import scala.scalajs.js.URIUtils
import scala.util.Try
import org.scalajs.dom.URLSearchParams

abstract class RoutePattern[A] { self =>

  protected def consume(segments: List[String], query: URLSearchParams): Option[(A, List[String])]
  protected def renderSegments(value: A): List[String]
  protected def renderQuery(value: A): List[(String, String)] = Nil

  final def /(segment: String): RoutePattern[A] =
    new RoutePattern[A] {
      override protected def consume(
          segments: List[String],
          query: URLSearchParams
      ): Option[(A, List[String])] =
        self.consume(segments, query).flatMap {
          case (value, head :: tail) if decodeSegment(head) == segment =>
            Some((value, tail))
          case _ =>
            None
        }

      override protected def renderSegments(value: A): List[String] =
        self.renderSegments(value) :+ segment

      override protected def renderQuery(value: A): List[(String, String)] =
        self.renderQuery(value)
    }

  final def /[B, Out](@unused _arg: Arg[B])(using
      codec: Codec[B],
      appender: Appender[A, B, Out]
  ): RoutePattern[Out] =
    new RoutePattern[Out] {
      override protected def consume(
          segments: List[String],
          query: URLSearchParams
      ): Option[(Out, List[String])] =
        self.consume(segments, query).flatMap {
          case (left, head :: tail) =>
            codec
              .decode(Some(decodeSegment(head)))
              .map(value => (appender.append(left, value), tail))
          case (left, Nil) =>
            codec.decode(None).map(value => (appender.append(left, value), Nil))
        }

      override protected def renderSegments(value: Out): List[String] = {
        val (left, right) = appender.split(value)
        val encoded = codec.encode(right).getOrElse {
          throw new IllegalArgumentException(
            s"Could not encode route argument: ${right}"
          )
        }
        self.renderSegments(left) :+ encoded
      }

      override protected def renderQuery(value: Out): List[(String, String)] =
        self.renderQuery(appender.split(value)._1)
    }

  final def &[B, Out](param: Param[B])(using
      codec: Codec[B],
      appender: Appender[A, B, Out]
  ): RoutePattern[Out] =
    new RoutePattern[Out] {
      override protected def consume(
          segments: List[String],
          query: URLSearchParams
      ): Option[(Out, List[String])] =
        self.consume(segments, query).flatMap { case (left, remaining) =>
          Option(query.get(param.name))
            .flatMap(value => codec.decode(Some(value)))
            .map(right => (appender.append(left, right), remaining))
        }

      override protected def renderSegments(value: Out): List[String] =
        self.renderSegments(appender.split(value)._1)

      override protected def renderQuery(value: Out): List[(String, String)] = {
        val (left, right) = appender.split(value)
        val encoded = codec.encode(right).getOrElse {
          throw new IllegalArgumentException(s"Could not encode query parameter: ${param.name}")
        }
        self.renderQuery(left) :+ (param.name -> encoded)
      }
    }

  final def url(value: A): String = {
    val segments = renderSegments(value).map(encodeSegment)
    val path = if (segments.isEmpty) "/" else "/" + segments.mkString("/")
    val query = new URLSearchParams()
    renderQuery(value).foreach { case (name, encoded) => query.append(name, encoded) }
    val suffix = query.toString
    if (suffix.isEmpty) path else path + "?" + suffix
  }

  /** Matches a relative URL; unknown query parameters and fragments are ignored. */
  final def unapply(url: String): Option[A] = {
    val withoutFragment = url.takeWhile(_ != '#')
    val queryStart = withoutFragment.indexOf('?')
    val (path, search) =
      if (queryStart < 0) (withoutFragment, "")
      else (withoutFragment.take(queryStart), withoutFragment.drop(queryStart + 1))
    Try(consume(RoutePattern.splitPath(path), new URLSearchParams(search)))
      .toOption.flatten.collect { case (value, Nil) => value }
  }

  protected final def encodeSegment(segment: String): String =
    URIUtils.encodeURIComponent(segment)

  protected final def decodeSegment(segment: String): String =
    URIUtils.decodeURIComponent(segment)
}

object Root extends RoutePattern[Unit] {
  override protected def consume(
      segments: List[String],
      query: URLSearchParams
  ): Option[(Unit, List[String])] =
    Some(((), segments))

  override protected def renderSegments(value: Unit): List[String] = Nil
}

private object RoutePattern {
  def splitPath(path: String): List[String] =
    path
      .split('/')
      .toList
      .filter(_.nonEmpty)
}
