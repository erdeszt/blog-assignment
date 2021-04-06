package assignment

import assignment.model.DomainError._
import assignment.model._
import assignment.service._
import cats.syntax.functor._
import cats.syntax.traverse._
import doobie.syntax.string._
import doobie.util.fragment.Fragment

import java.util.UUID
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestEnvironment
import zio.test.junit._

// TODO: Option[NonEmptyString] for title?
object ApiSpecSbtRunner extends ApiSpec
class ApiSpec extends JUnitRunnableSpec {

  val idRefLayer: ULayer[Has[FakeIdProvider.Ref]] =
    Ref.make[List[UUID]](Nil).map(FakeIdProvider.Ref).toLayer
  val testDatabaseConfig: ULayer[Has[DatabaseConfig]] = {
    ZEnv.live >>> ZLayer.fromEffect {
      for {
        rawPort <- system.env("DB_PORT").someOrElse("3306").orDie
        port <- UIO(rawPort.toInt)
      } yield DatabaseConfig(
        DatabaseConfig.Host("localhost"),
        DatabaseConfig.Port(port),
        DatabaseConfig.Database("assignment_test"),
        DatabaseConfig.User("root"),
        DatabaseConfig.Password("root"),
      )
    }
  }
  val stores       = testDatabaseConfig >>> Layers.stores
  val migration    = testDatabaseConfig >>> Migration.layer
  val dependencies = (migration ++ (idRefLayer >+> FakeIdProvider.layer) ++ stores) >+> Api.layer

  val randomUUID: URIO[random.Random, UUID] = UIO(UUID.randomUUID())

  val cleanDatabase: URIO[Has[TransactionHandler], Unit] =
    ZIO.accessM[Has[TransactionHandler]] { trx =>
      trx.get.run {
        for {
          _ <- sql"set foreign_key_checks = 0".update.run
          tables <- sql"show tables".query[String].to[List]
          _ <- tables.filter(_ != "flyway_schema_history").traverse[Trx, Unit] { table =>
            (fr"truncate table " ++ Fragment.const(table)).update.run.void
          }
          _ <- sql"set foreign_key_checks = 1".update.run
        } yield ()
      }
    }

  override def spec =
    (
      suite("Api")(
        suite("Create blog")(
          testM("should succeed with correct name") {
            for {
              expectedId <- randomUUID
              _ <- FakeIdProvider.set(expectedId)
              (blogId, _) <- Api.createBlog(Blog.Name("test blog"), Blog.Slug("test-blog"), List.empty).toDomainError
            } yield assert(blogId.value)(equalTo(expectedId))
          },
          testM("should create the posts") {
            for {
              expectedBlogId <- randomUUID
              expectedPostIds <- ZIO.replicateM(10)(randomUUID).map(_.toList)
              posts = expectedPostIds.map[(Option[Post.Title], Post.Content)] { postId =>
                (Some(Post.Title(s"Post #${postId}")), Post.Content("some content"))
              }
              _ <- FakeIdProvider.set(expectedBlogId :: expectedPostIds)

              (blogId, postIds) <- Api.createBlog(Blog.Name("test blog"), Blog.Slug("test-blog"), posts).toDomainError
            } yield assert(blogId.value)(equalTo(expectedBlogId)) &&
              assert(postIds.map(_.value))(equalTo(expectedPostIds))
          },
          testM("should fail if the name is empty") {
            for {
              error <- Api.createBlog(Blog.Name(""), Blog.Slug("ok"), List.empty).toDomainError.either
            } yield assert(error)(isLeft(equalTo(EmptyBlogName())))
          },
          testM("should fail if the slug is empty") {
            for {
              error <- Api.createBlog(Blog.Name("ok"), Blog.Slug(""), List.empty).toDomainError.either
            } yield assert(error)(isLeft(equalTo(EmptyBlogSlug())))
          },
          testM("should fail if the slug is invalid") {
            val slug = Blog.Slug("invalid+!")
            for {
              error <- Api.createBlog(Blog.Name("ok"), slug, List.empty).toDomainError.either
            } yield assert(error)(isLeft(equalTo(InvalidBlogSlug(slug))))
          },
          testM("should fail if the slug is already in use") {
            val slug = Blog.Slug("bad")
            for {
              blogId1 <- randomUUID
              blogId2 <- randomUUID
              _ <- FakeIdProvider.set(List(blogId1, blogId2))

              _ <- Api.createBlog(Blog.Name("ok"), slug, List.empty).toDomainError
              error <- Api.createBlog(Blog.Name("notok"), slug, List.empty).toDomainError.either
            } yield assert(error)(isLeft(equalTo(BlogSlugAlreadyExists(slug))))
          },
          testM("should fail if the content is empty") {
            for {
              error <- Api
                .createBlog(
                  Blog.Name("ok"),
                  Blog.Slug("ok"),
                  List((None, Post.Content("ok")), (None, Post.Content(""))),
                )
                .toDomainError
                .either
            } yield assert(error)(isLeft(equalTo(EmptyPostContent())))
          },
        ),
        suite("Create post")(
          testM("should succeed with correct input") {
            for {
              blogId <- randomUUID
              expectedPostId <- randomUUID
              _ <- FakeIdProvider.set(List(blogId, expectedPostId))

              _ <- Api.createBlog(Blog.Name("blog1"), Blog.Slug("blog1"), List.empty).toDomainError
              postId <- Api
                .createPost(Blog.Id(blogId), Some(Post.Title("post1")), Post.Content("post1 content"))
                .toDomainError
            } yield assert(postId.value)(equalTo(expectedPostId))
          },
          testM("should fail if the blog doesn't exist") {
            for {
              blogId <- randomUUID.map(Blog.Id)

              error <- Api
                .createPost(blogId, Some(Post.Title("post1")), Post.Content("post1 content"))
                .toDomainError
                .either
            } yield assert(error)(isLeft(equalTo(BlogNotFound(blogId))))
          },
          testM("should fail if the content is empty") {
            for {
              blogId <- randomUUID
              expectedPostId <- randomUUID
              _ <- FakeIdProvider.set(List(blogId, expectedPostId))

              _ <- Api.createBlog(Blog.Name("blog1"), Blog.Slug("blog1"), List.empty).toDomainError
              error <- Api
                .createPost(Blog.Id(blogId), Some(Post.Title("post1")), Post.Content(""))
                .toDomainError
                .either
            } yield assert(error)(isLeft(equalTo(EmptyPostContent())))
          },
        ),
        suite("Query blogs")(
          testM("should find a blog by id") {
            for {
              blogId <- randomUUID
              postIds <- ZIO.replicateM(10)(randomUUID).map(_.toList)
              posts = postIds.map(id => (None, Post.Content(s"Post: ${id}")))
              _ <- FakeIdProvider.set(blogId :: postIds)

              _ <- Api.createBlog(Blog.Name("test blog"), Blog.Slug("test-blog"), posts).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogId(Blog.Id(blogId)), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1))) &&
              assert(blogs.map(_.id.value))(hasSameElements(List(blogId))) &&
              assert(blogs.head.posts)(isEmpty)
          },
          testM("should return the posts if requested") {
            for {
              blogId <- randomUUID
              postIds <- ZIO.replicateM(10)(randomUUID).map(_.toList)
              posts = postIds.map(id => (None, Post.Content(s"Post: ${id}")))
              _ <- FakeIdProvider.set(blogId :: postIds)

              _ <- Api.createBlog(Blog.Name("test blog"), Blog.Slug("test-blog"), posts).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogId(Blog.Id(blogId)), includePosts = true)
            } yield assert(blogs)(hasSize(equalTo(1))) &&
              assert(blogs.head.posts.map(_.id.value))(hasSameElements(postIds))
          },
          testM("should find a blog by slug") {
            val slug = Blog.Slug("test-blog")
            for {
              blogId <- randomUUID
              _ <- FakeIdProvider.set(blogId)

              _ <- Api.createBlog(Blog.Name("test blog"), slug, List.empty).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogSlug(slug), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1)))
          },
          testM("should find a blog by exact name") {
            val name = Blog.Name("test blog")
            for {
              blogId <- randomUUID
              _ <- FakeIdProvider.set(blogId)

              _ <- Api.createBlog(name, Blog.Slug("test-blog"), List.empty).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogName(name), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1)))
          },
          testM("should find a blog by partial name") {
            for {
              blogId <- randomUUID
              _ <- FakeIdProvider.set(blogId)

              _ <- Api.createBlog(Blog.Name("test blog"), Blog.Slug("test-blog"), List.empty).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogName(Blog.Name("%test%")), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1)))
          },
          testM("should find a blog by exact post title") {
            for {
              blogId <- randomUUID
              postId <- randomUUID
              _ <- FakeIdProvider.set(List(blogId, postId))

              _ <- Api
                .createBlog(
                  Blog.Name("test blog"),
                  Blog.Slug("test-blog"),
                  List((Some(Post.Title("test title")), Post.Content("test content"))),
                )
                .toDomainError
              blogs <- Api.queryBlogs(Query.ByPostTitle(Post.Title("test title")), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1)))
          },
          testM("should find a blog by partial title") {
            for {
              blogId <- randomUUID
              postId <- randomUUID
              _ <- FakeIdProvider.set(List(blogId, postId))

              _ <- Api
                .createBlog(
                  Blog.Name("test blog"),
                  Blog.Slug("test-blog"),
                  List((Some(Post.Title("test title")), Post.Content("test content"))),
                )
                .toDomainError
              blogs <- Api.queryBlogs(Query.ByPostTitle(Post.Title("%test%")), includePosts = false)
            } yield assert(blogs)(hasSize(equalTo(1)))
          },
        ),
        testM("should find a blog by partial content") {
          for {
            blogId <- randomUUID
            postId <- randomUUID
            _ <- FakeIdProvider.set(List(blogId, postId))

            _ <- Api
              .createBlog(
                Blog.Name("test blog"),
                Blog.Slug("test-blog"),
                List((Some(Post.Title("test title")), Post.Content("test content"))),
              )
              .toDomainError
            blogs <- Api.queryBlogs(Query.ByPostContent(Post.Content("%test%")), includePosts = false)
          } yield assert(blogs)(hasSize(equalTo(1)))
        },
      ) @@ before(FakeIdProvider.set(Nil) *> cleanDatabase)
        @@ beforeAll(Migration.migrate)
        @@ sequential
    ).provideSomeLayer[TestEnvironment](dependencies)

}
