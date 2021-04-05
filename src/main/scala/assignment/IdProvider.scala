package assignment

import java.util.UUID
import zio._

type IdProvider = Has[IdProvider.Service]
  
object IdProvider:

  trait Service:
    def generateId: UIO[UUID]
    
  class Live extends IdProvider.Service:
    override def generateId: UIO[UUID] = UIO(UUID.randomUUID())

  val layer: ULayer[IdProvider] = ZLayer.succeed(Live())
  
