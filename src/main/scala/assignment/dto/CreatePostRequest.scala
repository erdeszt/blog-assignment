package assignment.dto

import assignment.model.Post
import io.circe.generic.semiauto.deriveCodec

final case class CreatePostRequest(
    title:   Option[Post.Title],
    content: Post.Content,
)

object CreatePostRequest {
  implicit val jsonCodec = deriveCodec[CreatePostRequest]
}
