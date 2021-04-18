package assignment

import assignment.model.{Blog, WithPosts}
import sttp.tapir.client.sttp._
import sttp.client3.{ignore => _, _}
import sttp.model.StatusCode
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.junit.JUnitRunnableSpec

import java.util.UUID

/**
  * The test can be run manually by starting up the server then removing the `@@ ignore` from the end of the suite and running `sbt test`
  * Given more time it could start up the server either directly on the host or in a docker container and run against it, tearing it down afterwards.
  */
object WebSpecSbtRunner extends WebSpec
class WebSpec extends JUnitRunnableSpec {

  override def spec =
    suite("Web API")(
      suite("Query")(
        testM("return 404 when to blog does not exist") {
          val request = SttpClientInterpreter.toRequest(Routes.getBlogById, Some(uri"http://localhost:8080"))
          val backend = HttpURLConnectionBackend()
          for {
            blogId <- UIO(UUID.randomUUID()).map(Blog.Id)
            response <- UIO(request((blogId, WithPosts.No)).send(backend))
          } yield assert(response.code)(equalTo(StatusCode.NotFound))
        },
      ),
    ) @@ ignore

}
