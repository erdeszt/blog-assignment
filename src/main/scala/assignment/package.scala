import doobie._

import cats.syntax.either._
import java.util.UUID
import scala.util.Try

package object assignment {
  type Trx[T] = ConnectionIO[T]

  trait DoobieUUIDUtils {
    implicit val putUUID: Put[UUID] = Put[String].contramap(_.toString)
    implicit val getUUID: Get[UUID] = Get[String].temap { uuid =>
      Try(UUID.fromString(uuid)).toEither.leftMap(_.getMessage)
    }
  }
}
