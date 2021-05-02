package assignment.service

import cats.effect.IO
import doobie._
import doobie.syntax.connectionio._
import org.atnos.eff._
import org.atnos.eff.addon.cats.effect.IOEffect._
import org.atnos.eff.interpret._

object TransactionHandler {

  sealed trait Op[+A]
  final case class Run[A](trx: Trx[A]) extends Op[A]

  type _trx[R] = Op |= R

  def run[R: _trx, A](trx: Trx[A]): Eff[R, A] = {
    Eff.send[Op, R, A](Run(trx))
  }

  def evalTransactionHandler[R, U, A](transactor: Transactor[IO])(
      effect:                                     Eff[R, A],
  )(implicit m:                                   Member.Aux[Op, R, U], io: _io[U]): Eff[U, A] = {
    translate(effect)(new Translate[Op, U] {
      override def apply[X](op: Op[X]): Eff[U, X] = {
        op match {
          case Run(trx) => fromIO(trx.transact(transactor))
        }
      }
    })
  }

  implicit class TransactionHandlerEvaluator[R, U, A](effect: Eff[R, A])(
      implicit member:                                        Member.Aux[Op, R, U],
      io:                                                     _io[U],
  ) {
    def runTransactionHandler(trx: Transactor[IO]): Eff[U, A] = evalTransactionHandler(trx)(effect)
  }

}
