package assignment.model

import io.circe.generic.semiauto._
import io.circe.Codec

import java.util.UUID

final case class Post(
    id:        Post.Id,
    blogId:    Blog.Id,
    title:     Option[Post.Title],
    content:   Post.Content,
    viewCount: Post.ViewCount,
)

object Post {

  final case class Id(value:        UUID) extends Newtype[UUID]
  final case class Title(value:     String) extends Newtype[String]
  final case class Content(value:   String) extends Newtype[String]
  final case class ViewCount(value: Long) extends Newtype[Long]

  implicit val idCodec:        Codec[Id]        = Newtype.deriveCireCodec(Id)
  implicit val titleCodec:     Codec[Title]     = Newtype.deriveCireCodec(Title)
  implicit val contentCodec:   Codec[Content]   = Newtype.deriveCireCodec(Content)
  implicit val viewCountCodec: Codec[ViewCount] = Newtype.deriveCireCodec(ViewCount)
  implicit val jsonCodec:      Codec[Post]      = deriveCodec[Post]

}
