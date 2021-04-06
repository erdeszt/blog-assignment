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

  val layers: URLayer[ZEnv, Has[Migration] with Has[Api]] =
    (DatabaseConfig.layer >>> Migration.layer) ++ Layers.api

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val server: ZIO[zio.ZEnv with Has[Api] with Has[Migration], Nothing, Unit] = for {
      _ <- Migration.migrate
      _ <- serve
    } yield ()

    server.provideCustomLayer(layers).exitCode
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
