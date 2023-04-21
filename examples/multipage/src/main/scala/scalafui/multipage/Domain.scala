package scalafui.multipage

object Domain {

  case class Work(
      id: String,
      title: String,
      authors: Option[List[String]],
      description: Option[String]
  )

  case class Edition(
      id: String,
      title: String,
      publishDate: Option[String],
      publishers: Option[List[String]],
      coverIds: Option[List[Int]]
  )
}
