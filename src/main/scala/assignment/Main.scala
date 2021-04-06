package assignment

import assignment.service._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.clock.Clock
import zio._
import zio.interop.catz._

import scala.annotation.nowarn

object Main extends CatsApp {

  val layers: URLayer[ZEnv, Has[Config] with Has[Migration] with Has[Api]] =
    (Config.DatabaseConfig.layer >>> Migration.layer) ++ Layers.api ++ Config.layer

  @nowarn // Dead code warnings for using the resource forever
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val server: ZIO[zio.ZEnv with Has[Config] with Has[Api] with Has[Migration], Nothing, Unit] = for {
      config <- ZIO.service[Config]
      _ <- Migration.migrate
      _ <- ZIO.runtime[ZEnv with Has[Api]].flatMap { implicit runtime =>
        BlazeServerBuilder[RIO[Has[Api] with Clock, *]](runtime.platform.executor.asEC)
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(
            Router("/" -> Routes.create(config.jwtSecret)).orNotFound,
          )
          .resource
          .use(_ => UIO.never)
          .orDie
      }
    } yield ()

    server.provideCustomLayer(layers).exitCode
  }

}
