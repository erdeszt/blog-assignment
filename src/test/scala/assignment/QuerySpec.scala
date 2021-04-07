package assignment

import assignment.model.Query2.Compiler._
import assignment.model.Query2._
import doobie.syntax.string._
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec

object QuerySpecSbtRunner extends QuerySpec
class QuerySpec extends JUnitRunnableSpec {

  val blogId            = "blog1"
  val blogIdSelector    = FieldSelector("blog", "id")
  val viewCountSelector = FieldSelector("post", "view_count")
  val postTitleSelector = FieldSelector("post", "title")

  // TODO: Type check fields
  override def spec = suite("Query")(
    test("compile conditions") {
      val queriesAndFragments = List(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId))   -> fr"blog.id = ${blogId}",
        Comparison(Comparison.Eq(), blogIdSelector, Value.Null())         -> fr"blog.id IS NULL",
        Comparison(Comparison.NotEq(), blogIdSelector, Value.Null())      -> fr"blog.id IS NOT NULL",
        Comparison(Comparison.Lt(), viewCountSelector, Value.Number(12))  -> fr"post.view_count < ${12}",
        Comparison(Comparison.Gt(), viewCountSelector, Value.Number(12))  -> fr"post.view_count > ${12}",
        Comparison(Comparison.Lte(), viewCountSelector, Value.Number(12)) -> fr"post.view_count <= ${12}",
        Comparison(Comparison.Gte(), viewCountSelector, Value.Number(12)) -> fr"post.view_count >= ${12}",
      )

      queriesAndFragments.foldLeft(assert(true)(equalTo(true))) {
        case (assertions, (query, expected)) =>
          val renderedQuery = compile(query)

          assertions && assert(renderedQuery.toString)(equalTo(expected.toString))
      }
    },
    testM("fail for invalid Null comparison") {
      val invalidOp = Comparison.Lt()
      val query     = Comparison(invalidOp, blogIdSelector, Value.Null())
      for {
        error <- Task(compile(query)).either
      } yield assert(error)(isLeft(equalTo(InvalidNullComparison(invalidOp))))
    },
    test("||") {
      val query = Or(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(equalTo(fr"(blog.id = ${blogId} ) OR (post.view_count > ${0} )".toString))
    },
    test("|| 3") {
      val query = Or(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        Or(
          Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
          Comparison(Comparison.NotEq(), postTitleSelector, Value.Null()),
        ),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(
        equalTo(fr"(blog.id = ${blogId} ) OR ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
      )
    },
    test("&&") {
      val query = And(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(equalTo(fr"(blog.id = ${blogId} ) AND (post.view_count > ${0} )".toString))
    },
    test("&& 3") {
      val query = And(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        And(
          Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
          Comparison(Comparison.NotEq(), postTitleSelector, Value.Null()),
        ),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(
        equalTo(fr"(blog.id = ${blogId} ) AND ((post.view_count > ${0} ) AND (post.title IS NOT NULL ) )".toString),
      )
    },
    test("|| &&") {
      val query = Or(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        And(
          Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
          Comparison(Comparison.NotEq(), postTitleSelector, Value.Null()),
        ),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(
        equalTo(fr"(blog.id = ${blogId} ) OR ((post.view_count > ${0} ) AND (post.title IS NOT NULL ) )".toString),
      )
    },
    test("&& || ") {
      val query = And(
        Comparison(Comparison.Eq(), blogIdSelector, Value.Text(blogId)),
        Or(
          Comparison(Comparison.Gt(), viewCountSelector, Value.Number(0)),
          Comparison(Comparison.NotEq(), postTitleSelector, Value.Null()),
        ),
      )
      val renderedQuery = compile(query)

      assert(renderedQuery.toString)(
        equalTo(fr"(blog.id = ${blogId} ) AND ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
      )
    },
  )

}
