package assignment

import assignment.dto.{QueryBlogsRequest, QueryBlogsResponse}
import assignment.model.Blog
import sttp.tapir.client.sttp._
import sttp.client3.{ignore => _, _}
import sttp.tapir.DecodeResult
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.junit.JUnitRunnableSpec

import java.util.UUID

object WebSpecSbtRunner extends WebSpec
class WebSpec extends JUnitRunnableSpec {

  override def spec =
    suite("Web API")(
      suite("Query")(
        testM("return an empty list when there are no blogs") {
          val request = SttpClientInterpreter.toRequest(Routes.queryBlogs, Some(uri"http://localhost:8080"))
          val backend = HttpURLConnectionBackend()
          for {
            response: Response[DecodeResult[Either[Routes.ErrorResponse, QueryBlogsResponse]]] <- UIO(
              request(QueryBlogsRequest(model.Query.ByBlogId(Blog.Id(UUID.randomUUID())), includePosts = false))
                .send(backend),
            )
          } yield assert(response.body)(equalTo(DecodeResult.Value(Right(QueryBlogsResponse(List.empty)))))
        },
      ),
    ) @@ ignore

}
