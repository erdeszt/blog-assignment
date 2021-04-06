package assignment.model

sealed trait Query
object Query {
  final case class ByBlogId(id:     Blog.Id) extends Query
  final case class ByBlogSlug(slug: Blog.Slug) extends Query
}
