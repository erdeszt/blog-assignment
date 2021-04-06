package assignment.model

sealed trait Query
object Query {
  final case class ByBlogId(id: Blog.Id) extends Query
}
