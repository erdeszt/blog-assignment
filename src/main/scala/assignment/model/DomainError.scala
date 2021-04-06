package assignment.model

sealed abstract class DomainError(val message: String) extends Exception(message)
object DomainError {
  final case class EmptyBlogName() extends DomainError("Blog name is empty")
  final case class EmptyPostBody() extends DomainError("Post body is empty")
}
