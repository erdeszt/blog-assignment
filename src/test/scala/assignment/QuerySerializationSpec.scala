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
    test("should support all query types") {
      val blogId      = UUID.randomUUID()
      val blogSlug    = "slug"
      val blogName    = "name"
      val postTitle   = "title"
      val postContent = "content"
      val queriesAndJsons = List[(model.Query, String)](
        (model.Query.ByBlogId(Blog.Id(blogId)), s"""{"ByBlogId":{"id":"${blogId}"}}"""),
        (model.Query.ByBlogSlug(Blog.Slug(blogSlug)), s"""{"ByBlogSlug":{"slug":"${blogSlug}"}}"""),
        (model.Query.ByBlogName(Blog.Name(blogName)), s"""{"ByBlogName":{"name":"${blogName}"}}"""),
        (model.Query.HasPosts(), s"""{"HasPosts":{}}"""),
        (model.Query.ByPostTitle(Post.Title(postTitle)), s"""{"ByPostTitle":{"title":"${postTitle}"}}"""),
        (model.Query.ByPostContent(Post.Content(postContent)), s"""{"ByPostContent":{"content":"${postContent}"}}"""),
      )

      queriesAndJsons.foldLeft(assert(true)(equalTo(true))) {
        case (assertions, (query, json)) =>
          assertions && assert(query.asJson.noSpaces)(equalTo(json))
      }
    },
  )

}
