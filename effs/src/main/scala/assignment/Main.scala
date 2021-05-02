package assignment

import assignment.service.Api
import cats.effect._
import doobie.{ExecutionContexts, Transactor}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  val transactor = Transactor.fromDriverManager[IO](
    "com.mysql.cj.jdbc.Driver",
    s"jdbc:mysql://127.0.0.1:3306/assignment",
    "root",
    "root",
  )

  def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(Router("/" -> Routes.create(transactor)).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

}
