package assignment

import assignment.model._
import assignment.service._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.clock.Clock
import zio._
import zio.interop.catz._

/**
  * TODO:
  *    - Logging
  *    - ?Auth?
  *    - Tests
  *    - Docs
  *    - Dockerize
  *    - Setup CI
  */
//object Main extends App {
object Main extends CatsApp {

  // TODO: Remove, setup env vars
  val dbConfig = DatabaseConfig(
    DatabaseConfig.Host("localhost"),
    DatabaseConfig.Port(3306),
    DatabaseConfig.Database("assignment"),
    DatabaseConfig.User("root"),
    DatabaseConfig.Password("root")
  )

  val apiLayer = ZLayer.succeed {
    new Api {
      def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[(Blog.Id, List[Post.Id])] = ???

      def createPost(blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): UIO[Post.Id] = ???

      def queryBlogs(query: Query): UIO[List[Blog]] = ???
    }
  }

  val layers
      : ZLayer[Any, Nothing, Has[Migration] with Has[Api]] = (ZLayer.succeed(dbConfig) >>> Migration.layer) ++ apiLayer

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val server: ZIO[zio.ZEnv with Has[Api] with Has[Migration], Nothing, Unit] = for {
      _ <- Migration.migrate
      _ <- serve
    } yield ()

    server.provideSomeLayer[ZEnv](layers).exitCode
  }

  // TODO: Swagger docs
  // TODO: Inline?
  def serve: URIO[ZEnv with Has[Api], Unit] = {
    ZIO.runtime[ZEnv with Has[Api]].flatMap { implicit runtime =>
      BlazeServerBuilder[RIO[Has[Api] with Clock, *]](runtime.platform.executor.asEC)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(Router("/" -> Routes.create()).orNotFound)
        .resource
        .use(_ => UIO.never)
        .orDie
    }
  }

}
