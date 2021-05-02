package assignment.model

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

  implicit val idCodec        = Newtype.deriveCireCodec(Id)
  implicit val titleCodec     = Newtype.deriveCireCodec(Title)
  implicit val contentCodec   = Newtype.deriveCireCodec(Content)
  implicit val viewCountCodec = Newtype.deriveCireCodec(ViewCount)

}
