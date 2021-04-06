package assignment

import assignment.model._
import assignment.dto.QueryBlogsRequest.queryCodec
import io.circe.syntax._
import java.util.UUID
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec

object QuerySerializationSbtRunner extends QuerySerializationSpec
class QuerySerializationSpec extends JUnitRunnableSpec {

  override def spec = suite("Query serialization")(
    testM("should support all query types") {
      val blogId = UUID.randomUUID()
      val queriesAndJsons = List[(model.Query, String)](
        (model.Query.ByBlogId(Blog.Id(blogId)), s"""{"ByBlogId":{"id":"${blogId}"}}"""),
      )

      UIO {
        queriesAndJsons.foldLeft(assert(true)(equalTo(true))) {
          case (assertions, (query, json)) =>
            assertions && assert(query.asJson.noSpaces)(equalTo(json))
        }
      }
    },
  )

}
