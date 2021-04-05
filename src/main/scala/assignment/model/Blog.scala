package assignment.model

import java.util.UUID

final case class Blog(
    id: Blog.Id,
    name: Blog.Name,
    slug: Blog.Slug,
    posts: List[Post],
  )

object Blog {

  final case class Id(value: UUID) extends AnyVal
  final case class Name(value: String) extends AnyVal
  final case class Slug(value: String) extends AnyVal

}