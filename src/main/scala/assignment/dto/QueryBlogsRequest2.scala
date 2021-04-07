package assignment.dto

import assignment.model.Query2
import assignment.model.Query2.FieldSelector
import io.circe.Decoder.Result
import io.circe.{Codec, DecodingFailure, HCursor, Json}
import io.circe.generic.semiauto.deriveCodec

final case class QueryBlogsRequest2(
    query:        Query2.Condition,
    includePosts: Boolean,
)

object QueryBlogsRequest2 {

  implicit val jsonCodec = deriveCodec[QueryBlogsRequest2]

  implicit val query2Codec: Codec[Query2.Condition] = deriveCodec[Query2.Condition]

  implicit val binOpCodec: Codec[Query2.BinOpType] = mappingCodec[Query2.BinOpType](
    Map[String, Query2.BinOpType](
      "eq"    -> Query2.Eq(),
      "noteq" -> Query2.NotEq(),
      "lt"    -> Query2.Lt(),
      "gt"    -> Query2.Gt(),
      "lte"   -> Query2.Lte(),
      "gte"   -> Query2.Gte(),
      "like"  -> Query2.Like(),
    ),
    "binop",
  )

  implicit val unOpCodec: Codec[Query2.UnOpType] = mappingCodec[Query2.UnOpType](
    Map[String, Query2.UnOpType](
      "isnull"    -> Query2.IsNull(),
      "isnotnull" -> Query2.IsNotNull(),
    ),
    "unop",
  )

  implicit val fieldSelectorCodec: Codec[FieldSelector] = mappingCodec[Query2.FieldSelector](
    Map[String, Query2.FieldSelector](
      "blog.id"         -> FieldSelector.Blog.Id(),
      "blog.name"       -> FieldSelector.Blog.Name(),
      "blog.slug"       -> FieldSelector.Blog.Slug(),
      "post.id"         -> FieldSelector.Post.Id(),
      "post.title"      -> FieldSelector.Post.Title(),
      "post.content"    -> FieldSelector.Post.Content(),
      "post.view_count" -> FieldSelector.Post.ViewCount(),
    ),
    "field selector",
  )

  implicit val valueCodec: Codec[Query2.Value] = new Codec[Query2.Value] {
    def apply(cursor: HCursor): Result[Query2.Value] = {
      cursor
        .as[String]
        .map(text => Query2.Value.Text(text))
        .orElse(cursor.as[Long].map(number => Query2.Value.Number(number)))
    }
    def apply(value: Query2.Value): Json = {
      value match {
        case Query2.Value.Number(number) => Json.fromLong(number)
        case Query2.Value.Text(text)     => Json.fromString(text)
      }
    }
  }

  def mappingCodec[T](mapping: Map[String, T], tpe: String): Codec[T] = {
    new Codec[T] {
      private val inverseMapping = mapping.map { case (key, value) => (value, key) }
      def apply(cursor: HCursor): Result[T] = {
        cursor.as[String].flatMap { raw =>
          mapping.get(raw) match {
            case None        => Left(DecodingFailure(s"Invalid ${tpe}: ${raw}", List.empty))
            case Some(binop) => Right(binop)
          }
        }
      }
      def apply(value: T): Json = {
        Json.fromString(inverseMapping.getOrElse(value, throw new Exception(s"Invalid ${tpe}: ${value}")))
      }
    }
  }

}
