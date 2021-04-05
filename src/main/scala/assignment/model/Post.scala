package assignment.model

import java.util.UUID

final case class Post(
    id:        Post.Id,
    blogId:    Blog.Id,
    title:     Option[Post.Title],
    body:      Post.Body,
    viewCount: Post.ViewCount
)

object Post {

  final case class Id(value:        UUID) extends AnyVal
  final case class Title(value:     String) extends AnyVal
  final case class Body(value:      String) extends AnyVal
  final case class ViewCount(value: Long) extends AnyVal

}
