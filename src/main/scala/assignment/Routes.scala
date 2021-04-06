package assignment

import assignment.dto._
import assignment.model.DomainError.EmptyBlogName
import assignment.model._
import assignment.service._
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import hotpotato._
import shapeless.Coproduct
import shapeless.ops.coproduct.Unifier
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
      message: String,
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

  def errorHandler(error: DomainError): ErrorResponse = {
    error match {
      case DomainError.EmptyBlogName() => ErrorResponse(1, error.getMessage)
      case DomainError.EmptyBlogSlug() => ErrorResponse(2, error.getMessage)
      case DomainError.EmptyPostBody() => ErrorResponse(3, error.getMessage)
      case DomainError.BlogNotFound(_) => ErrorResponse(4, error.getMessage)
    }
  }

  def create(): HttpRoutes[RIO[Has[Api] with Clock, *]] = {
    val createBlogRoute = Routes.createBlog.zServerLogic { request =>
      Api
        .createBlog(request.name, request.slug, request.posts.map(post => (post.title, post.body)))
        .handleDomainErrors(errorHandler)
        .map {
          case (blogId, postIds) =>
            CreateBlogResponse(blogId, postIds)
        }
    }
    val createPostRoute = Routes.createPost.zServerLogic { request =>
      Api
        .createPost(request.blogId, request.create.title, request.create.body)
        .handleDomainErrors(errorHandler)
        .map(CreatePostResponse(_))
    }
    // TODO: Request type
    val queryBlogsRoute = Routes.queryBlogs.zServerLogic { request =>
      Api.queryBlogs(Query.ByBlogId(Blog.Id(UUID.randomUUID())), includePosts = true).map(QueryBlogsResponse(_))
    }

    ZHttp4sServerInterpreter.from(List(createBlogRoute, createPostRoute, queryBlogsRoute)).toRoutes
  }

}
