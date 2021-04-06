package assignment

import assignment.service.IdProvider

import java.util.UUID
import zio._

final case class FakeIdProvider(idRef: FakeIdProvider.Ref) extends IdProvider {
  override def generateId: UIO[UUID] = {
    idRef.value
      .modify {
        case Nil       => (None, Nil)
        case id :: ids => (Some(id), ids)
      }
      .someOrFail(new Exception("No ids to provide"))
      .orDie
  }
}

object FakeIdProvider {
  final case class Ref(value: zio.Ref[List[UUID]])

  val layer: URLayer[Has[Ref], Has[IdProvider]] = ZLayer.fromService(FakeIdProvider(_))

  def set(value: UUID): URIO[Has[Ref], Unit] = {
    ZIO.accessM(_.get.value.set(List(value)))
  }

  def set(values: List[UUID]): URIO[Has[Ref], Unit] = {
    ZIO.accessM(_.get.value.set(values))
  }

}
