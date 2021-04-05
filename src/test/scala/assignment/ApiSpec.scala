package assignment

import assignment.model._
import assignment.model._
import assignment.model.Query
import java.util.UUID
import zio._
import zio.test._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.junit._

class ApiSpec extends JUnitRunnableSpec:

  val idRefLayer: ULayer[Has[FakeIdProvider.Ref]] = 
    ZLayer.fromEffect(Ref.make[Option[UUID]](None).map(FakeIdProvider.Ref(_)))
  val dependencies: ULayer[Has[FakeIdProvider.Ref] with Api] =
    idRefLayer >+> (FakeIdProvider.layer >>> Api.layer)

  override def spec = suite("Api")(
    suite("Create blog")(
      testM("X should succeed with correct name") {
        for
        expectedId <- UIO(UUID.randomUUID())
        _ <- FakeIdProvider.set(expectedId)
        blogId <- Api.createBlog(Blog.Name("test blog"), List.empty)
          yield assert(blogId)(equalTo(expectedId))
      }.provideSomeLayer[TestEnvironment](dependencies)
    )
  )


