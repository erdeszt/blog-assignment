package assignment.dto

import assignment.model.Blog
import io.circe.generic.semiauto.deriveCodec

final case class CreatePostForBlogRequest(
    blogId: Blog.Id,
    create: CreatePostRequest
)

object CreatePostForBlogRequest {
  implicit val jsonCodec = deriveCodec[CreatePostForBlogRequest]
}
