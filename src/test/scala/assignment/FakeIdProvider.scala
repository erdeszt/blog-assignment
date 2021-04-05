package assignment

import java.util.UUID
import zio._

class FakeIdProvider(idRef: FakeIdProvider.Ref) extends IdProvider.Service:
  override def generateId: UIO[UUID] = idRef.value.get.someOrFail(new Exception("Id is not set")).orDie
  
object FakeIdProvider:
  final case class Ref(value: zio.Ref[Option[UUID]])

  val layer: URLayer[Has[Ref], IdProvider] = ZLayer.fromService(FakeIdProvider(_))
  
  def set(value: UUID): URIO[Has[Ref], Unit] =
    ZIO.accessM(_.get.value.set(Some(value)))
