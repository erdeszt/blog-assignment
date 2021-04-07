package assignment

import assignment.model.Query2.Compiler._
import assignment.model.Query2.TypeChecker.NullComparisonWithNonNullableField
import assignment.model.Query2._
import doobie.syntax.string._
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec

object QuerySpecSbtRunner extends QuerySpec
class QuerySpec extends JUnitRunnableSpec {

  val blogId            = "blog1"
  val blogIdSelector    = FieldSelector.Blog.Id()
  val viewCountSelector = FieldSelector.Post.ViewCount()
  val postTitleSelector = FieldSelector.Post.Title()

  // TODO: Type check fields
  override def spec = suite("Query")(
    suite("Compiler")(
      test("compile conditions") {
        val queriesAndFragments = List(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId))   -> fr"blog.id = ${blogId}",
          UnOp(IsNull(), blogIdSelector)                    -> fr"blog.id IS NULL",
          UnOp(IsNotNull(), blogIdSelector)                 -> fr"blog.id IS NOT NULL",
          BinOp(Lt(), viewCountSelector, Value.Number(12))  -> fr"post.view_count < ${12}",
          BinOp(Gt(), viewCountSelector, Value.Number(12))  -> fr"post.view_count > ${12}",
          BinOp(Lte(), viewCountSelector, Value.Number(12)) -> fr"post.view_count <= ${12}",
          BinOp(Gte(), viewCountSelector, Value.Number(12)) -> fr"post.view_count >= ${12}",
        )

        queriesAndFragments.foldLeft(assert(true)(equalTo(true))) {
          case (assertions, (query, expected)) =>
            val renderedQuery = compile(query)

            assertions && assert(renderedQuery.toString)(equalTo(expected.toString))
        }
      },
      test("||") {
        val query = Or(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          BinOp(Gt(), viewCountSelector, Value.Number(0)),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(equalTo(fr"(blog.id = ${blogId} ) OR (post.view_count > ${0} )".toString))
      },
      test("|| 3") {
        val query = Or(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          Or(
            BinOp(Gt(), viewCountSelector, Value.Number(0)),
            UnOp(IsNotNull(), postTitleSelector),
          ),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) OR ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
        )
      },
      test("&&") {
        val query = And(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          BinOp(Gt(), viewCountSelector, Value.Number(0)),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(equalTo(fr"(blog.id = ${blogId} ) AND (post.view_count > ${0} )".toString))
      },
      test("&& 3") {
        val query = And(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          And(
            BinOp(Gt(), viewCountSelector, Value.Number(0)),
            UnOp(IsNotNull(), postTitleSelector),
          ),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) AND ((post.view_count > ${0} ) AND (post.title IS NOT NULL ) )".toString),
        )
      },
      test("|| &&") {
        val query = Or(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          And(
            BinOp(Gt(), viewCountSelector, Value.Number(0)),
            UnOp(IsNotNull(), postTitleSelector),
          ),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) OR ((post.view_count > ${0} ) AND (post.title IS NOT NULL ) )".toString),
        )
      },
      test("&& || ") {
        val query = And(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          Or(
            BinOp(Gt(), viewCountSelector, Value.Number(0)),
            UnOp(IsNotNull(), postTitleSelector),
          ),
        )
        val renderedQuery = compile(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) AND ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
        )
      },
    ),
    suite("Type checker")(
      testM("should not allow null comparison with non nullable fields") {
        val query = UnOp(IsNull(), blogIdSelector)
        for {
          error <- Task(TypeChecker.check(query)).either
        } yield assert(error)(isLeft(equalTo(NullComparisonWithNonNullableField(blogIdSelector))))
      },
    ),
  )

}
