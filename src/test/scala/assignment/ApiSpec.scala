package assignment

import assignment.model.Blog.{Id, Name}
import assignment.model.Post.{Body, Title}
import assignment.model.Query
import zio._
import zio.duration.{given}
import zio.test._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.junit._

trait Test:
  val test: Test.Service
  
object Test:
  trait Service:
    def foo(): UIO[Unit]
    
  def foo(): URIO[Has[Test], Unit] =
    ZIO.accessM(_.get.test.foo())

  object Live extends Test:
    val test = new Service:
      override def foo(): UIO[Unit] = UIO.unit

  val layer: ULayer[Has[Test]] = ZLayer.succeed(Live)

class ApiSpec extends JUnitRunnableSpec:

  object FakeApi extends Api:
    val api = new Api.Service:
      override def createBlog(name: Name, posts: List[(Option[Title], Body)]) = ZIO.die(new Exception("TODO"))
      override def createPost(blogId: Id, title: Title, body: Body) = ZIO.die(new Exception("TODO"))
      override def queryBlogs(query: Query) = ZIO.die(new Exception("TODO"))
      override def dummy() = UIO.unit
    val layer: ULayer[Has[Api]] = ZLayer.succeed(FakeApi)

  override def spec = suite("Api")(
    suite("Create blog")(
      testM("should succeed with correct name")(
        for
          _ <- UIO(println("OK"))
          _ <- Api.dummy()
          _ <- TestClock.adjust(1.minute)
          _ <- Test.foo()
        yield assert(true)(equalTo(true))
      ).provideSomeLayer[TestEnvironment](Test.layer ++ FakeApi.layer)
    )
  )


