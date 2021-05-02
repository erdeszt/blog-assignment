package assignment.service

import cats.syntax.either._
import doobie._
import java.util.UUID
import scala.util.Try

trait UUIDDatabaseMapping {
  implicit val putUUID: Put[UUID] = Put[String].contramap(_.toString)
  implicit val getUUID: Get[UUID] = Get[String].temap { uuid =>
    Try(UUID.fromString(uuid)).toEither.leftMap(_.getMessage)
  }
}
