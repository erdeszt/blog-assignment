package assignment

import assignment.dto.CreateBlogResponse
import assignment.model.Blog
import assignment.service._
import cats.effect.{ConcurrentEffect, Timer}
import cats.syntax.all._
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import sttp.tapir._
import sttp.tapir.ztapir._
import zio.clock.Clock
//import sttp.tapir.server.http4s._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

/**
  * TODO:
  *    - Setup server
  *    - Routing
  *    - Logging
  *    - ?Auth?
  *    - Tests
  *    - Docs
  *    - Dockerize
  *    - Setup CI
  */
//object Main extends App {
object Main extends zio.interop.catz.CatsApp {

  // TODO: Remove, setup env vars
  val dbConfig = DatabaseConfig(
    DatabaseConfig.Host("localhost"),
    DatabaseConfig.Port(3306),
    DatabaseConfig.Database("assignment"),
    DatabaseConfig.User("root"),
    DatabaseConfig.Password("root")
  )

  val layers = ZLayer.succeed(dbConfig) >>> Migration.layer

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val server = for {
      _ <- Migration.migrate
      routes <- Routes.create
      _ <- serve(routes)
    } yield ()

//    server.provideSomeLayer[ZEnv](layers).exitCode
    ???
  }
  // TODO: Swagger docs
  // TODO: Inline?
  def serve(routes: HttpRoutes[RIO[Clock, *]]): URIO[ZEnv, Unit] = {
    ZIO.runtime[ZEnv].flatMap { implicit runtime =>
      BlazeServerBuilder[RIO[Clock, *]](runtime.platform.executor.asEC)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(Router("/" -> routes).orNotFound)
        .serve
        .compile
        .drain
        .orDie
    }
  }

}
