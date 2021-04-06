package assignment.dto

import assignment.model._
import io.circe.generic.semiauto.deriveCodec

final case class CreatePostResponse(
    postId: Post.Id
)

object CreatePostResponse {
  implicit val jsonCodec = deriveCodec[CreatePostResponse]
}
