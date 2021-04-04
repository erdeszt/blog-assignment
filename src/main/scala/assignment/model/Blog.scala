package assignment.model

import java.util.UUID

final case class Blog(
    id: Blog.Id,
    name: Blog.Name,
    slug: Blog.Slug,
    posts: List[Post],
  )

object Blog:

  opaque type Id = UUID
  object Id:
    def apply(value: UUID): Id = value

  opaque type Name = String
  object Name:
    def apply(value: String): Name = value

  opaque type Slug = String
  object Slug:
    def apply(value: String): Slug = value
