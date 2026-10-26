package fui.router

import org.scalatest.funsuite.AnyFunSuite

class RoutePatternSpec extends AnyFunSuite {
  case class Id(value: String)

  implicit object IdCodec extends Codec[Id] {
    override def encode(value: Id): Option[String] = Some(value.value)
    override def decode(value: Option[String]): Option[Id] = value.map(Id(_))
  }

  private val index = Root
  private val node = Root / "nodes" / Arg[Id]()
  private val nested = Root / "nodes" / Arg[Id]() / "cotonomas" / Arg[Id]()

  test("root route matches only the root path") {
    assert(index.url(()) == "/")
    assert(index.unapply("/").contains(()))
    assert(index.unapply("").contains(()))
    assert(index.unapply("/nodes").isEmpty)
  }

  test("single-argument route round-trips") {
    val nodeId = Id("abc-123")

    assert(node.url(nodeId) == "/nodes/abc-123")
    assert(node.unapply("/nodes/abc-123").contains(nodeId))
    assert(node.unapply("/nodes").isEmpty)
  }

  test("multi-argument route round-trips") {
    val nodeId = Id("node-1")
    val cotonomaId = Id("cotonoma-9")

    assert(
      nested.url((nodeId, cotonomaId)) == "/nodes/node-1/cotonomas/cotonoma-9"
    )
    assert(
      nested.unapply("/nodes/node-1/cotonomas/cotonoma-9").contains(
        (nodeId, cotonomaId)
      )
    )
  }

  test("route arguments are URI encoded") {
    val nodeId = Id("name with/slash")

    assert(node.url(nodeId) == "/nodes/name%20with%2Fslash")
    assert(node.unapply("/nodes/name%20with%2Fslash").contains(nodeId))
  }

  private val search = Root & Param[String]("query")

  test("required query distinguishes missing and empty values") {
    assert(search.unapply("/").isEmpty)
    assert(search.unapply("/?query=").contains(""))
    assert(search.unapply("/?query").contains(""))
    assert(search.url("") == "/?query=")
  }

  test("queries round-trip reserved characters and Unicode") {
    Seq("日本語", "two words", "+", "&", "/", "#", "a?b=c%20").foreach { value =>
      assert(search.unapply(search.url(value)).contains(value))
    }
    assert(search.unapply("/?query=a+b%2Bc").contains("a b+c"))
    assert(search.url("a b+c") == "/?query=a+b%2Bc")
  }

  test("first duplicate wins and unknown parameters and fragments are ignored") {
    assert(search.unapply("/?other=1&query=first&query=second#query=ignored").contains("first"))
    assert(Root.unapply("/?query=hello#section").contains(()))
    assert(node.unapply("/nodes/abc?query=hello#section").contains(Id("abc")))
    assert(search.unapply("/other?query=hello").isEmpty)
  }

  test("multiple typed queries combine with path arguments") {
    val route = Root / "work" / Arg[String]() & Param[String]("query") & Param[Int]("page")
    val value = ("id/1", "日本語 + & / #", 2)
    assert(route.unapply(route.url(value)).contains(value))
    assert(route.unapply("/work/id?page=2&query=hello").contains(("id", "hello", 2)))
    assert(route.unapply("/work/id?query=hello").isEmpty)
    assert(route.unapply("/work/id?query=hello&page=bad").isEmpty)
    assert(route.unapply("/work/id?query=hello&page=").isEmpty)
  }

  test("query parameter names are encoded and later path composition preserves queries") {
    val route = (Root & Param[String]("a & b")) / "work" / Arg[Int]()
    assert(route.unapply(route.url(("value", 3))).contains(("value", 3)))
  }

  test("malformed path encoding does not throw") {
    assert(node.unapply("/nodes/%ZZ").isEmpty)
  }
}
