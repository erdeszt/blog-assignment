package assignment

import assignment.Config.JwtSecret
import assignment.dto._
import assignment.model._
import assignment.service._
import cats.syntax.all._
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import org.http4s._
import pdi.jwt.{JwtAlgorithm, JwtCirce}
import sttp.tapir
import sttp.tapir.{Codec, CodecFormat, Endpoint, Schema}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import zio._
import zio.clock.Clock
import zio.interop.catz._

object Routes {

  final case class ErrorResponse(
      code:    Int,
      message: String,
  )

  implicit val errorEncoder = deriveCodec[ErrorResponse]

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

  def userCodec(jwtSecret: JwtSecret) = {
    new Codec[List[String], User, CodecFormat.TextPlain] {
      def schema: Typeclass[User]       = Schema.string
      def format: CodecFormat.TextPlain = CodecFormat.TextPlain()
      def rawDecode(parts: List[String]): tapir.DecodeResult[User] = {
        val raw = parts.mkString("")

        JwtCirce.decodeJson(raw, jwtSecret.value, List(JwtAlgorithm.HS256)).flatMap(_.as[User].toTry).toEither match {
          case Left(error)  => tapir.DecodeResult.Error("Invalid jwt token", error)
          case Right(value) => tapir.DecodeResult.Value(value)
        }
      }
      def encode(user: User): List[String] = {
        List(JwtCirce.encode(user.asJson, jwtSecret.value, JwtAlgorithm.HS256))
      }
    }
  }

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
      case DomainError.InvalidBlogSlug(_)       => ErrorResponse(3, error.getMessage)
      case DomainError.EmptyPostTitle()         => ErrorResponse(4, error.getMessage)
      case DomainError.EmptyPostContent()       => ErrorResponse(5, error.getMessage)
      case DomainError.BlogNotFound(_)          => ErrorResponse(6, error.getMessage)
      case DomainError.BlogSlugAlreadyExists(_) => ErrorResponse(7, error.getMessage)
    }
  }

  def create(jwtSecret: JwtSecret): HttpRoutes[RIO[Has[Api] with Clock, *]] = {
    val createBlogAuthenticated: ZEndpoint[(CreateBlogRequest, User), ErrorResponse, CreateBlogResponse] =
      createBlog.in(auth.bearer[User]()(userCodec(jwtSecret)))
    val createPostAuthenticated: ZEndpoint[(CreatePostForBlogRequest, User), ErrorResponse, CreatePostResponse] =
      createPost.in(auth.bearer[User]()(userCodec(jwtSecret)))
    val queryBlogsAuthenticated: ZEndpoint[(QueryBlogsRequest, User), ErrorResponse, QueryBlogsResponse] =
      queryBlogs.in(auth.bearer[User]()(userCodec(jwtSecret)))

    val createBlogRoute = createBlogAuthenticated.zServerLogic {
      case (request, user) =>
        Api
          .createBlog(request.name, request.slug, request.posts.map(post => (post.title, post.content)))
          .handleDomainErrors(errorHandler)
          .provideSomeLayer[Has[Api]](ZLayer.succeed(user))
          .map {
            case (blogId, postIds) =>
              CreateBlogResponse(blogId, postIds)
          }
    }
    val createPostRoute = createPostAuthenticated.zServerLogic {
      case (request, user) =>
        Api
          .createPost(request.blogId, request.create.title, request.create.content)
          .handleDomainErrors(errorHandler)
          .provideSomeLayer[Has[Api]](ZLayer.succeed(user))
          .map(CreatePostResponse(_))
    }
    val queryBlogsRoute = queryBlogsAuthenticated.zServerLogic {
      case (request, _) =>
        Api.queryBlogs(request.query, request.includePosts).map(QueryBlogsResponse(_))
    }
    val yaml: String = {
      import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
      import sttp.tapir.openapi.circe.yaml._

      OpenAPIDocsInterpreter
        .toOpenAPI(
          List(createBlogAuthenticated, createPostAuthenticated, queryBlogsAuthenticated),
          "Blog API",
          "1.0",
        )
        .toYaml
    }

    ZHttp4sServerInterpreter.from(List(createBlogRoute, createPostRoute, queryBlogsRoute)).toRoutes <+>
      new SwaggerHttp4s(yaml).routes[RIO[Has[Api] with Clock, *]]
  }

}
