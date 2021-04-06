package assignment.dto

import assignment.model._
import io.circe.generic.semiauto.deriveCodec

final case class QueryBlogsResponse(
    blogs: List[Blog]
)

object QueryBlogsResponse {

  implicit val jsonCodec = deriveCodec[QueryBlogsResponse]
}
