package assignment

import assignment.dto._
import assignment.model.Blog
import assignment.service.Api
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
//import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import zio._
import zio.clock.Clock
import zio.interop.catz._

import java.util.UUID

object Routes {

  final case class ErrorResponse(
      code:    Int,
      message: String
  )

  implicit val errorEncoder = deriveCodec[ErrorResponse]

  val createBlog: ZEndpoint[CreateBlogRequest, ErrorResponse, CreateBlogResponse] =
    endpoint.post
      .in("blog")
      .in(jsonBody[CreateBlogRequest])
      .out(jsonBody[CreateBlogResponse])
      .errorOut(jsonBody[ErrorResponse])

  val createPost: ZEndpoint[CreatePostForBlogRequest, ErrorResponse, CreatePostResponse] =
    endpoint.post
      .in("post")
      .in(jsonBody[CreatePostForBlogRequest])
      .out(jsonBody[CreatePostResponse])
      .errorOut(jsonBody[ErrorResponse])

  val queryBlogs: ZEndpoint[Unit, ErrorResponse, QueryBlogsResponse] =
    endpoint.get
      .in("blog" / "query")
      .out(jsonBody[QueryBlogsResponse])
      .errorOut(jsonBody[ErrorResponse])

  def create: URIO[Has[Api], HttpRoutes[RIO[Clock, *]]] = {
    ZIO.service[Api].map { api =>
      val createBlogRoute = Routes.createBlog.zServerLogic { i =>
        UIO(CreateBlogResponse(Blog.Id(UUID.randomUUID()), List.empty))
      }
      val createPostRoute = Routes.createPost.zServerLogic(i => ???)
      val queryBlogsRoute = Routes.queryBlogs.zServerLogic(i => ???)

      ZHttp4sServerInterpreter.from(List(createBlogRoute, createPostRoute, queryBlogsRoute)).toRoutes
    }
  }

}
