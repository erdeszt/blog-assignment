package assignment.service

import zio._

import java.util.UUID

trait IdProvider {
  def generateId: UIO[UUID]
}

object IdProvider {

  object Live extends IdProvider {
    override def generateId: UIO[UUID] = UIO(UUID.randomUUID())
  }

  val layer: ULayer[Has[IdProvider]] = ZLayer.succeed(Live)

}
