package assignment.dto

import assignment.model._
import io.circe.generic.semiauto._

final case class CreateBlogResponse(
    blogId:  Blog.Id,
    postIds: List[Post.Id]
)

object CreateBlogResponse {
  implicit val jsonCodec = deriveCodec[CreateBlogResponse]
}
