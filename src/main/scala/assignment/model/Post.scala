package assignment.model

import java.util.UUID

final case class Post(
  id: Post.Id,
  blogId: Blog.Id,
  title: Option[Post.Title],
  body: Post.Body,
  viewCount: Post.ViewCount,
)

object Post:
  opaque type Id = UUID
  object Id:
    def apply(value: UUID): Id = value

  opaque type Title = UUID
  object Title:
    def apply(value: UUID): Title = value

  opaque type Body = UUID
  object Body:
    def apply(value: UUID): Body = value

  opaque type ViewCount = UUID
  object ViewCount:
    def apply(value: UUID): ViewCount = value
