package assignment.service

import org.atnos.eff._
import org.atnos.eff.addon.cats.effect.IOEffect._
import org.atnos.eff.all._
import org.atnos.eff.addon.cats.effect._
import org.atnos.eff.interpret._

import java.util.UUID

/**
  * Interface for generating unique identifiers to enable controlling the ids during tests
  */
object IdProvider {

  sealed trait Op[+A]
  final case class GenerateId() extends Op[UUID]

  type _idProvider[R] = Op |= R

  def generateId[R: _idProvider]: Eff[R, UUID] = {
    Eff.send[Op, R, UUID](GenerateId())
  }

  def evalIdProvider[R, U, A](
      effect:   Eff[R, A],
  )(implicit m: Member.Aux[Op, R, U], io: _io[U]): Eff[U, A] = {
    translate(effect)(new Translate[Op, U] {
      override def apply[X](op: Op[X]): Eff[U, X] = {
        op match {
          case GenerateId() => ioDelay(UUID.randomUUID())
        }
      }
    })
  }

}
