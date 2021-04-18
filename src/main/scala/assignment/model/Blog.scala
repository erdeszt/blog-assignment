package assignment.model

import io.circe.Codec
import io.circe.generic.semiauto._

import java.util.UUID

final case class Blog(
    id:    Blog.Id,
    name:  Blog.Name,
    slug:  Blog.Slug,
    posts: List[Post],
)

object Blog {

  final case class Id(value:   UUID) extends Newtype[UUID]
  final case class Name(value: String) extends Newtype[String]
  final case class Slug(value: String) extends Newtype[String]

  implicit val idCodec:   Codec[Id]   = Newtype.deriveCireCodec(Id)
  implicit val nameCodec: Codec[Name] = Newtype.deriveCireCodec(Name)
  implicit val slugCodec: Codec[Slug] = Newtype.deriveCireCodec(Slug)
  implicit val jsonCodec: Codec[Blog] = deriveCodec[Blog]

}
