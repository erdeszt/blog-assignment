package assignment.service

import org.atnos.eff._
import org.atnos.eff.addon.cats.effect.IOEffect._
import org.atnos.eff.all._
import org.atnos.eff.addon.cats.effect._
import org.atnos.eff.interpret._

import java.util.UUID

sealed trait IdProviderOp[+A]
final case class GenerateId() extends IdProviderOp[UUID]

/**
  * Interface for generating unique identifiers to enable controlling the ids during tests
  */
object IdProvider {

  type _idProvider[R] = IdProviderOp |= R

  def generateId[R: _idProvider]: Eff[R, UUID] = {
    Eff.send[IdProviderOp, R, UUID](GenerateId())
  }

  def evalIdProvider[R, U, A](
      effect:   Eff[R, A],
  )(implicit m: Member.Aux[IdProviderOp, R, U], io: _io[U]): Eff[U, A] = {
    translate(effect)(new Translate[IdProviderOp, U] {
      override def apply[X](op: IdProviderOp[X]): Eff[U, X] = {
        op match {
          case GenerateId() => ioDelay(UUID.randomUUID())
        }
      }
    })
  }

}
