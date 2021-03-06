package assignment.service

import doobie._
import doobie.syntax.connectionio._
import zio._
import zio.interop.catz._

/**
  * Interface for executing transactions. Enables the separation of stores by domain model while allowing
  * multiple different writes to be combined into one transaction at the business logic level.
  */
trait TransactionHandler {
  def run[T](transaction: Trx[T]): UIO[T]
}

object TransactionHandler {

  final case class Live(transactor: Transactor[Task]) extends TransactionHandler {
    override def run[T](transaction: Trx[T]): UIO[T] = {
      transaction.transact(transactor).orDie
    }
  }

  val layer: URLayer[Has[Transactor[Task]], Has[TransactionHandler]] =
    ZLayer.fromService(Live)

  def run[T](transaction: Trx[T]): URIO[Has[TransactionHandler], T] = {
    ZIO.accessM(_.get.run(transaction))
  }

}
