package assignment

import assignment.model.DomainError
import cats.syntax.either._
import doobie._
import shapeless.Coproduct
import shapeless.ops.coproduct.Unifier

import java.util.UUID
import scala.util.Try
import zio._

package object service {
  type Trx[T] = ConnectionIO[T]

  trait DoobieUUIDUtils {
    implicit val putUUID: Put[UUID] = Put[String].contramap(_.toString)
    implicit val getUUID: Get[UUID] = Get[String].temap { uuid =>
      Try(UUID.fromString(uuid)).toEither.leftMap(_.getMessage)
    }
  }

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
