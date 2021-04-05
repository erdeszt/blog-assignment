package assignment

import cats.effect.Blocker
import doobie._
import zio._
import zio.interop.catz._

object Layers {

  val transactor: URLayer[Has[DatabaseConfig], Has[Transactor[Task]]] =
    ZLayer.fromService { config =>
      Transactor.fromDriverManager(
        "com.mysql.cj.jdbc.Driver",
        s"jdbc:mysql://${config.host.value}:${config.port.value}/${config.database.value}",
        config.user.value,
        config.password.value,
        Blocker.liftExecutionContext(ExecutionContexts.synchronous)
      )
    }

}
