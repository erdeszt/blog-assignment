package assignment.model

sealed abstract class DomainError(val message: String) extends Exception(message)
object DomainError {
  final case class EmptyBlogName() extends DomainError("Blog name is empty")
  final case class EmptyBlogSlug() extends DomainError("Blog slug is empty")
  final case class EmptyPostBody() extends DomainError("Post body is empty")
  final case class BlogNotFound(blogId:        Blog.Id) extends DomainError("Blog not found")
  final case class BlogSlugAlreadyExists(slug: Blog.Slug) extends DomainError("Blog slug already exists found")
}
