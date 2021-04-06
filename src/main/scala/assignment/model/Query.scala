package assignment.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait Query
object Query {
  final case class ByBlogId(id:     Blog.Id) extends Query
  final case class ByBlogSlug(slug: Blog.Slug) extends Query

  implicit val circeCodec: Codec[Query] = deriveCodec[Query]
}
