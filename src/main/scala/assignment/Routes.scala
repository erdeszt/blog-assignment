package assignment

import assignment.dto._
import assignment.model._
import assignment.service._
import cats.syntax.all._
import io.circe.generic.semiauto.deriveCodec
import org.http4s._
import sttp.model.StatusCode
//import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{Codec, Schema}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import zio._
import zio.clock.Clock
import zio.interop.catz._

import java.util.UUID
import scala.util.Try

object Routes {

  sealed abstract class ErrorResponse(val code:          Int, val message:          String)
  final case class BadRequestResponse(override val code: Int, override val message: String)
      extends ErrorResponse(code, message)
  final case class NotFoundResponse(override val code: Int, override val message: String)
      extends ErrorResponse(code, message)

  implicit val errorEncoder            = deriveCodec[ErrorResponse]
  implicit val badRequestResponseCodec = deriveCodec[BadRequestResponse]
  implicit val notFoundResponseCodec   = deriveCodec[NotFoundResponse]
  implicit val postJsonCodec           = deriveCodec[Post]
  implicit val blogJsonCodec           = deriveCodec[Blog]

  implicit val blogIdSchema: Schema[Blog.Id] =
    Schema.schemaForUUID.map(raw => Some(Blog.Id(raw)))(_.value)
  implicit val blogNameSchema: Schema[Blog.Name] = Schema.string
  implicit val blogSlugSchema: Schema[Blog.Slug] = Schema.string
  implicit val postIdSchema: Schema[Post.Id] =
    Schema.schemaForUUID.map(raw => Some(Post.Id(raw)))(_.value)
  implicit val postTitleSchema:   Schema[Post.Title]   = Schema.string
  implicit val postContentSchema: Schema[Post.Content] = Schema.string
  implicit val postViewCountSchema: Schema[Post.ViewCount] =
    Schema.schemaForLong.map(raw => Some(Post.ViewCount(raw)))(_.value)

  implicit val blogIdCodec: Codec[String, Blog.Id, TextPlain] = Codec.string.mapDecode({ raw =>
    Try(UUID.fromString(raw)).toEither match {
      case Left(error) => sttp.tapir.DecodeResult.Error(raw, error)
      case Right(id)   => sttp.tapir.DecodeResult.Value(Blog.Id(id))
    }
  })(_.value.toString)

  implicit val blogSlugCodec: Codec[String, Blog.Slug, TextPlain] = Codec.string.mapDecode({ raw =>
    sttp.tapir.DecodeResult.Value(Blog.Slug(raw))
  })(_.value)

  val createBlog: ZEndpoint[CreateBlogRequest, ErrorResponse, CreateBlogResponse] =
    endpoint.post
      .in("blog")
      .in(jsonBody[CreateBlogRequest])
      .out(jsonBody[CreateBlogResponse])
      .errorOut(oneOf[ErrorResponse](statusMapping(StatusCode.BadRequest, jsonBody[ErrorResponse])))

  val createPost: ZEndpoint[CreatePostForBlogRequest, ErrorResponse, CreatePostResponse] =
    endpoint.post
      .in("post")
      .in(jsonBody[CreatePostForBlogRequest])
      .out(jsonBody[CreatePostResponse])
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.NotFound, jsonBody[NotFoundResponse]),
          statusMapping(StatusCode.BadRequest, jsonBody[BadRequestResponse]),
        ),
      )

  val getBlogById: ZEndpoint[(Blog.Id, Boolean), ErrorResponse, Blog] =
    endpoint.get
      .in("blog" / path[Blog.Id]("blogId") / query[Boolean]("withPosts"))
      .out(jsonBody[Blog])
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.NotFound, jsonBody[NotFoundResponse]),
        ),
      )

  val getBlogBySlug: ZEndpoint[(Blog.Slug, Boolean), ErrorResponse, Blog] =
    endpoint.get
      .in("blog" / "slug" / path[Blog.Slug]("slug") / query[Boolean]("withPosts"))
      .out(jsonBody[Blog])
      .errorOut(
        oneOf[ErrorResponse](
          statusMapping(StatusCode.NotFound, jsonBody[NotFoundResponse]),
        ),
      )

  def errorHandler(error: DomainError): ErrorResponse = {
    error match {
      case DomainError.EmptyBlogName()          => BadRequestResponse(1, error.getMessage)
      case DomainError.EmptyBlogSlug()          => BadRequestResponse(2, error.getMessage)
      case DomainError.InvalidBlogSlug(_)       => BadRequestResponse(3, error.getMessage)
      case DomainError.EmptyPostTitle()         => BadRequestResponse(4, error.getMessage)
      case DomainError.EmptyPostContent()       => BadRequestResponse(5, error.getMessage)
      case DomainError.BlogSlugAlreadyExists(_) => NotFoundResponse(6, error.getMessage)
      case DomainError.BlogNotFound(_)          => NotFoundResponse(7, error.getMessage)
      case DomainError.BlogSlugNotFound(_)      => NotFoundResponse(8, error.getMessage)
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
    val getBlogByIdRoute = getBlogById.zServerLogic {
      case (blogId, withPosts) =>
        Api
          .getBlogById(blogId, withPosts)
          .mapError(errorHandler)
    }
    val getBlogBySlugRoute = getBlogBySlug.zServerLogic {
      case (slug, withPosts) =>
        Api
          .getBlogBySlug(slug, withPosts)
          .mapError(errorHandler)
    }

    ZHttp4sServerInterpreter
      .from(List(createBlogRoute, createPostRoute, getBlogByIdRoute, getBlogBySlugRoute))
      .toRoutes <+>
      new SwaggerHttp4s(Routes.yaml).routes[RIO[Has[Api] with Clock, *]]
  }

  val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._

    OpenAPIDocsInterpreter
      .toOpenAPI(
        List(createBlog, createPost, getBlogById, getBlogBySlug),
        "Blog API",
        "1.0",
      )
      .toYaml
  }

}
