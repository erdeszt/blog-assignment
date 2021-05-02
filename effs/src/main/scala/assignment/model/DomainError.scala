package assignment.model

sealed abstract class DomainError(val message: String) extends Exception(message)
object DomainError {
  final case class EmptyBlogName() extends DomainError("Blog name is empty")
  final case class EmptyBlogSlug() extends DomainError("Blog slug is empty")
  final case class InvalidBlogSlug(slug: Blog.Slug) extends DomainError("Blog slug is invalid")
  final case class EmptyPostTitle() extends DomainError("Post title was provided but it was empty")
  final case class EmptyPostContent() extends DomainError("Post content is empty")
  final case class BlogNotFound(blogId:        Blog.Id) extends DomainError("Blog not found")
  final case class BlogSlugAlreadyExists(slug: Blog.Slug) extends DomainError("Blog slug already exists")
}
