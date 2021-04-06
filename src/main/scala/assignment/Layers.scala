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

  val stores: URLayer[Has[DatabaseConfig], Has[TransactionHandler] with Has[BlogStore] with Has[PostStore]] =
    transactionHandler >+> (PostStore.layer ++ BlogStore.layer)

  val api: URLayer[ZEnv, Has[Api]] = {
    val apiDependencies = IdProvider.layer ++ (DatabaseConfig.layer >>> stores)

    apiDependencies >>> Api.layer
  }

}
