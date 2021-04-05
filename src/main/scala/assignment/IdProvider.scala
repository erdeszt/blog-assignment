package assignment

import java.util.UUID
import zio._

trait IdProvider {
  def generateId: UIO[UUID]
}

object IdProvider {

  object Live extends IdProvider {
    override def generateId: UIO[UUID] = UIO(UUID.randomUUID())
  }

  val layer: ULayer[Has[IdProvider]] = ZLayer.succeed(Live)

}
  
