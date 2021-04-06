package assignment.model

sealed trait Query
object Query {
  final case class ByBlogId(id:     Blog.Id) extends Query
  final case class ByBlogSlug(slug: Blog.Slug) extends Query
  final case class ByBlogName(name: Blog.Name) extends Query
  final case class HasPosts() extends Query
  final case class ByPostTitle(title:     Post.Title) extends Query
  final case class ByPostContent(content: Post.Content) extends Query
}
