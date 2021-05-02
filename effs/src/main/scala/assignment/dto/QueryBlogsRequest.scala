package assignment.dto

import assignment.model.Query
import io.circe.generic.semiauto.deriveCodec

final case class QueryBlogsRequest(
    query:        Query,
    includePosts: Boolean,
)

object QueryBlogsRequest {
  implicit val queryCodec = deriveCodec[Query]
  implicit val jsonCodec  = deriveCodec[QueryBlogsRequest]
}
