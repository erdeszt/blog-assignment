package assignment

import assignment.dto._
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object Routes {

  final case class ErrorResponse(
      code:    Int,
      message: String
  )

  implicit val errorEncoder = deriveCodec[ErrorResponse]

  val createBlog: Endpoint[CreateBlogRequest, ErrorResponse, CreateBlogResponse, Any] =
    endpoint.post
      .in("blog")
      .in(jsonBody[CreateBlogRequest])
      .out(jsonBody[CreateBlogResponse])
      .errorOut(jsonBody[ErrorResponse])

  val createPost: Endpoint[CreatePostForBlogRequest, ErrorResponse, CreatePostResponse, Any] =
    endpoint.post
      .in("post")
      .in(jsonBody[CreatePostForBlogRequest])
      .out(jsonBody[CreatePostResponse])
      .errorOut(jsonBody[ErrorResponse])

  val queryBlogs: Endpoint[Unit, ErrorResponse, QueryBlogsResponse, Any] =
    endpoint.get
      .in("blog" / "query")
      .out(jsonBody[QueryBlogsResponse])
      .errorOut(jsonBody[ErrorResponse])

}
