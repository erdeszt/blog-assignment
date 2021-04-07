package assignment.service

import zio._

import java.util.UUID

/**
  * Interface for generating unique identifiers to enable controlling the ids during tests
  */
trait IdProvider {
  def generateId: UIO[UUID]
}

object IdProvider {

  object Live extends IdProvider {
    override def generateId: UIO[UUID] = UIO(UUID.randomUUID())
  }

  val layer: ULayer[Has[IdProvider]] = ZLayer.succeed(Live)

}
