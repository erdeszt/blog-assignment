package assignment.model

import io.circe.generic.semiauto.deriveCodec
import java.util.UUID

final case class User(id: User.Id)

object User {

  final case class Id(value: UUID) extends Newtype[UUID]

  implicit val idCodec   = Newtype.deriveCireCodec(Id)
  implicit val jsonCodec = deriveCodec[User]

}
