package assignment

import assignment.dto.QueryBlogsResponse
import sttp.tapir.client.sttp._
import sttp.client3.{ignore => _, _}
import sttp.tapir.DecodeResult
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.junit.JUnitRunnableSpec

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
              request(()).send(backend),
            )
          } yield assert(response.body)(equalTo(DecodeResult.Value(Right(QueryBlogsResponse(List.empty)))))
        },
      ),
    ) @@ ignore // TODO: Start server or CI

}
