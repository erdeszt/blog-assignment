package assignment.model

import io.circe._
import io.circe.Decoder.Result

trait Newtype[T] {
  val value: T
}

object Newtype {

  def deriveCireCodec[T, NT <: Newtype[T]](constructor: T => NT)(
      implicit
      encoder: Encoder[T],
      decoder: Decoder[T]
  ): Codec[NT] = {
    new Codec[NT] {
      def apply(newtype: NT):      Json       = encoder.apply(newtype.value)
      def apply(cursor:  HCursor): Result[NT] = decoder.apply(cursor).map(constructor)
    }
  }

}
