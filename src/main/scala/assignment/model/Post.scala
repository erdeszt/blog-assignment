package assignment.model

import io.circe._
import java.util.UUID

final case class Post(
    id:        Post.Id,
    blogId:    Blog.Id,
    title:     Option[Post.Title],
    body:      Post.Body,
    viewCount: Post.ViewCount,
)

object Post {

  final case class Id(value:        UUID) extends Newtype[UUID]
  final case class Title(value:     String) extends Newtype[String]
  final case class Body(value:      String) extends Newtype[String]
  final case class ViewCount(value: Long) extends Newtype[Long]

  implicit val idCodec        = Newtype.deriveCireCodec(Id)
  implicit val titleCodec     = Newtype.deriveCireCodec(Title)
  implicit val bodyCodec      = Newtype.deriveCireCodec(Body)
  implicit val viewCountCodec = Newtype.deriveCireCodec(ViewCount)

}
