package assignment

import assignment.model.DomainError.EmptyBlogName
import assignment.model._
import assignment.service._

import java.util.UUID
import zio._
import zio.random.Random
import zio.test._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.junit._

class ApiSpec extends JUnitRunnableSpec {

  val idRefLayer: ULayer[Has[FakeIdProvider.Ref]] =
    ZLayer.fromEffect(Ref.make[List[UUID]](Nil).map(FakeIdProvider.Ref))
  val testDatabaseConfig: ULayer[Has[DatabaseConfig]] = ZLayer.succeed(
    DatabaseConfig(
      DatabaseConfig.Host("localhost"),
      DatabaseConfig.Port(3306),
      DatabaseConfig.Database("assignment_test"),
      DatabaseConfig.User("root"),
      DatabaseConfig.Password("root")
    )
  )
  val transactionHandler = (testDatabaseConfig >>> Layers.transactor) >>> TransactionHandler.layer
  val blogStore          = transactionHandler >>> BlogStore.layer
  val postStore          = transactionHandler >>> PostStore.layer
  val idProvider         = idRefLayer >>> FakeIdProvider.layer
  val dependencies =
    idRefLayer ++ ((idProvider ++ blogStore ++ postStore ++ transactionHandler) >>> Api.layer)

  val randomUUID: URIO[random.Random, UUID] = UIO(UUID.randomUUID())

  // TODO: Clean the database
  val cleanDatabase = UIO(println("Clean database"))

  override def spec =
    (
      suite("Api")(
        suite("Create blog")(
          testM("should succeed with correct name")(
            for {
              expectedId <- randomUUID
              _ <- FakeIdProvider.set(expectedId)
              (blogId, _) <- Api.createBlog(Blog.Name("test blog"), List.empty).toDomainError
            } yield assert(blogId.value)(equalTo(expectedId))
          ),
          testM("should create the posts")(
            for {
              expectedBlogId <- randomUUID
              expectedPostIds <- ZIO.replicateM(10)(randomUUID).map(_.toList)
              posts = expectedPostIds.map[(Option[Post.Title], Post.Body)] { postId =>
                (Some(Post.Title(s"Post #${postId}")), Post.Body("some content"))
              }
              _ <- FakeIdProvider.set(expectedBlogId :: expectedPostIds)

              (blogId, postIds) <- Api.createBlog(Blog.Name("test blog"), posts).toDomainError
            } yield assert(blogId.value)(equalTo(expectedBlogId)) &&
              assert(postIds.map(_.value))(equalTo(expectedPostIds))
          ),
          testM("should fail for empty name")(
            for {
              error <- Api.createBlog(Blog.Name(""), List.empty).toDomainError.either
            } yield assert(error)(isLeft(equalTo(EmptyBlogName())))
          )
        ),
        suite("Create post")(
          testM("should succeed with correct input")(
            for {
              blogId <- randomUUID
              expectedPostId <- randomUUID
              _ <- FakeIdProvider.set(expectedPostId)

              postId <- Api.createPost(Blog.Id(blogId), Some(Post.Title("post1")), Post.Body("post1 body"))
            } yield assert(postId.value)(equalTo(expectedPostId))
          )
        ),
        suite("Query blogs")(
          testM("should find a blog by id") {
            for {
              blogId <- randomUUID
              _ <- FakeIdProvider.set(blogId)

              _ <- Api.createBlog(Blog.Name("test blog"), List.empty).toDomainError
              blogs <- Api.queryBlogs(Query.ByBlogId(Blog.Id(blogId)))
            } yield assert(blogs.map(_.id.value))(equalTo(List(blogId)))
          }
        )
      ) @@ before(FakeIdProvider.set(Nil) *> cleanDatabase)
        @@ beforeAll(Migration.migrate.provideLayer(testDatabaseConfig >>> Migration.layer))
    ).provideSomeLayer[TestEnvironment](dependencies)

}
