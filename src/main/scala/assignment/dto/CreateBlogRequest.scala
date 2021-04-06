package assignment.dto

import assignment.model.Blog
import io.circe.generic.semiauto.deriveCodec

final case class CreateBlogRequest(
    name:  Blog.Name,
    slug:  Blog.Slug,
    posts: List[CreatePostRequest],
)

object CreateBlogRequest {
  implicit val jsonCodec = deriveCodec[CreateBlogRequest]
}
