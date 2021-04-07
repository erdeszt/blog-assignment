package assignment

import assignment.model.Query2._
import assignment.model.Query2.Compiler._
import assignment.model.Query2.TypeChecker._
import doobie.Fragment
import doobie.syntax.string._
import zio.test._
import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec

object QuerySpecSbtRunner extends Query2Spec
class Query2Spec extends JUnitRunnableSpec {

  val blogId            = "blog1"
  val blogIdSelector    = FieldSelector.Blog.Id()
  val viewCountSelector = FieldSelector.Post.ViewCount()
  val postTitleSelector = FieldSelector.Post.Title()

  def runCompiler(query: Condition): Fragment = {
    compileCondition(query).run(CompilerState(needsJoinedFields = false)).value._2
  }

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
            val renderedQuery = runCompiler(query)

            assertions && assert(renderedQuery.toString)(equalTo(expected.toString))
        }
      },
      test("||") {
        val query = Or(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          BinOp(Gt(), viewCountSelector, Value.Number(0)),
        )
        val renderedQuery = runCompiler(query)

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
        val renderedQuery = runCompiler(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) OR ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
        )
      },
      test("&&") {
        val query = And(
          BinOp(Eq(), blogIdSelector, Value.Text(blogId)),
          BinOp(Gt(), viewCountSelector, Value.Number(0)),
        )
        val renderedQuery = runCompiler(query)

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
        val renderedQuery = runCompiler(query)

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
        val renderedQuery = runCompiler(query)

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
        val renderedQuery = runCompiler(query)

        assert(renderedQuery.toString)(
          equalTo(fr"(blog.id = ${blogId} ) AND ((post.view_count > ${0} ) OR (post.title IS NOT NULL ) )".toString),
        )
      },
    ),
    suite("Type checker")(
      test("should not allow null comparison with non nullable fields") {
        val query = UnOp(IsNull(), blogIdSelector)
        val error = TypeChecker.check(query)

        assert(error)(isLeft(equalTo(NullComparisonWithNonNullableField(blogIdSelector))))
      },
      test("should match the types of the two sides of binary operators") {
        val value = Value.Number(1)
        val query = BinOp(Eq(), blogIdSelector, value)
        val error = TypeChecker.check(query)

        assert(error)(isLeft(equalTo(TypeMismatch(blogIdSelector, value))))
      },
      test("should not allow numerical arguments to string operators") {
        val value = Value.Number(1)
        val query = BinOp(Like(), viewCountSelector, value)
        val error = TypeChecker.check(query)

        assert(error)(isLeft(equalTo(InvalidOperatorForType(Like(), viewCountSelector))))
      },
      test("should not allow string arguments to numeric operators") {
        val value = Value.Text("test")
        val query = BinOp(Lt(), blogIdSelector, value)
        val error = TypeChecker.check(query)

        assert(error)(isLeft(equalTo(InvalidOperatorForType(Lt(), blogIdSelector))))
      },
    ),
  )

}
