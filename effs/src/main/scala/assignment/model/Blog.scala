package assignment.model

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

  implicit val idCodec   = Newtype.deriveCireCodec(Id)
  implicit val nameCodec = Newtype.deriveCireCodec(Name)
  implicit val slugCodec = Newtype.deriveCireCodec(Slug)

}
