package assignment

import assignment.service._
import cats.effect.Blocker
import doobie._
import zio._
import zio.interop.catz._

object Layers {

  val transactionHandler: URLayer[Has[DatabaseConfig], Has[TransactionHandler]] =
    ZLayer.fromService { config =>
      TransactionHandler.Live(
        Transactor.fromDriverManager(
          "com.mysql.cj.jdbc.Driver",
          s"jdbc:mysql://${config.host.value}:${config.port.value}/${config.database.value}",
          config.user.value,
          config.password.value,
          Blocker.liftExecutionContext(ExecutionContexts.synchronous),
        ),
      )
    }

  val api: URLayer[ZEnv, Has[Api]] = {
    val stores          = (DatabaseConfig.layer >>> transactionHandler) >+> (PostStore.layer ++ BlogStore.layer)
    val apiDependencies = IdProvider.layer ++ stores

    apiDependencies >>> Api.layer
  }

}
