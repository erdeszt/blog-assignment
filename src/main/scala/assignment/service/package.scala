package assignment

import assignment.model.DomainError
import doobie._
import shapeless.Coproduct
import shapeless.ops.coproduct.Unifier
import zio._

package object service {
  type Trx[T] = ConnectionIO[T]

  implicit class ErrorExtension[-R, E <: Coproduct, +A](effect: ZIO[R, E, A])(
      implicit unifier:                                         Unifier.Aux[E, DomainError],
  ) {
    def toDomainError: ZIO[R, DomainError, A] = {
      handleDomainErrors(identity)
    }
    def handleDomainErrors[EOut](handler: DomainError => EOut): ZIO[R, EOut, A] = {
      effect.mapError(error => handler(error.unify))
    }
  }
}
