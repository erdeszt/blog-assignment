package assignment.model

sealed trait Query
object Query {
  final case class ById(id: Blog.Id) extends Query
}
