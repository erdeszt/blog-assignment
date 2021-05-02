package assignment

import doobie._
import org.atnos.eff._
import shapeless.Coproduct

package object service {

  /**
    * Database transaction effect type
    */
  type Trx[T] = ConnectionIO[T]

  type _error[E <: Coproduct, R] = Either[E, *] |= R

}
