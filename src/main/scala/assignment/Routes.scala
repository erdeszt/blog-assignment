package assignment

import assignment.dto._
import assignment.model._
import assignment.service._
import cats.syntax.all._
import io.circe.generic.semiauto.deriveCodec
import org.http4s._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
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

  val queryBlogs: ZEndpoint[QueryBlogsRequest, ErrorResponse, QueryBlogsResponse] =
    endpoint.post
      .in("blog" / "query")
      .in(jsonBody[QueryBlogsRequest])
      .out(jsonBody[QueryBlogsResponse])
      .errorOut(jsonBody[ErrorResponse])

  def errorHandler(error: DomainError): ErrorResponse = {
    error match {
      case DomainError.EmptyBlogName()          => ErrorResponse(1, error.getMessage)
      case DomainError.EmptyBlogSlug()          => ErrorResponse(2, error.getMessage)
      case DomainError.EmptyPostContent()       => ErrorResponse(3, error.getMessage)
      case DomainError.BlogNotFound(_)          => ErrorResponse(4, error.getMessage)
      case DomainError.BlogSlugAlreadyExists(_) => ErrorResponse(5, error.getMessage)
      case DomainError.InvalidBlogSlug(_)       => ErrorResponse(6, error.getMessage)
    }
  }

  def create(): HttpRoutes[RIO[Has[Api] with Clock, *]] = {
    val createBlogRoute = createBlog.zServerLogic { request =>
      Api
        .createBlog(request.name, request.slug, request.posts.map(post => (post.title, post.content)))
        .handleDomainErrors(errorHandler)
        .map {
          case (blogId, postIds) =>
            CreateBlogResponse(blogId, postIds)
        }
    }
    val createPostRoute = createPost.zServerLogic { request =>
      Api
        .createPost(request.blogId, request.create.title, request.create.content)
        .handleDomainErrors(errorHandler)
        .map(CreatePostResponse(_))
    }
    val queryBlogsRoute = queryBlogs.zServerLogic { request =>
      Api.queryBlogs(request.query, request.includePosts).map(QueryBlogsResponse(_))
    }

    ZHttp4sServerInterpreter.from(List(createBlogRoute, createPostRoute, queryBlogsRoute)).toRoutes <+>
      new SwaggerHttp4s(Routes.yaml).routes[RIO[Has[Api] with Clock, *]]
  }

  val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._

    OpenAPIDocsInterpreter
      .toOpenAPI(
        List(createBlog, createPost, queryBlogs),
        "Blog API",
        "1.0",
      )
      .toYaml
  }

}
