package assignment.model

import assignment.model
import cats.data.State
import doobie._
import doobie.syntax.string._

// TODO: `HAVING` for `post.view_count`
object Query2 {

  /*

  Example queries:

  - blog.name LIKE '%string%'
    {
      "query": {
        "BinOp": { "op": "like", "field": "blog.name", "value": "%string%" }
      },
      "includePosts": true
    }

  - blog.name LIKE '%string' AND post.title is not null
    {
      "query": {
        "And" {
          "left": "BinOp": { "op": "like", "field": "blog.name", "value": "%string%" },
          "right": "UnOp": { "op": "isnotnull", "field": "post.title" },
        }
      },
      "includePosts": true
    }

   */

  sealed trait Condition
  final case class BinOp(op: BinOpType, field: FieldSelector, value: Value) extends Condition
  final case class UnOp(op:  UnOpType, field:  FieldSelector) extends Condition
  final case class And(left: Condition, right: Condition) extends Condition
  final case class Or(left:  Condition, right: Condition) extends Condition

  sealed trait BinOpType
  final case class Eq() extends BinOpType
  final case class NotEq() extends BinOpType
  final case class Lt() extends BinOpType
  final case class Gt() extends BinOpType
  final case class Lte() extends BinOpType
  final case class Gte() extends BinOpType
  final case class Like() extends BinOpType

  sealed trait UnOpType
  final case class IsNull() extends UnOpType
  final case class IsNotNull() extends UnOpType

  sealed abstract class FieldSelector(val ty: Ty, val nullable: Boolean)
  object FieldSelector {
    object Blog {
      final case class Id() extends FieldSelector(TString(), nullable   = false)
      final case class Name() extends FieldSelector(TString(), nullable = false)
      final case class Slug() extends FieldSelector(TString(), nullable = false)
    }
    object Post {
      final case class Id() extends FieldSelector(TString(), nullable      = false)
      final case class Title() extends FieldSelector(TString(), nullable   = true)
      final case class Content() extends FieldSelector(TString(), nullable = false)
      final case class ViewCount() extends FieldSelector(TInt(), nullable  = false)
    }
  }

  sealed trait Ty
  final case class TInt() extends Ty
  final case class TString() extends Ty

  sealed abstract class Value(val ty: Ty)
  object Value {
    final case class Number(value: Long) extends Value(TInt())
    final case class Text(value:   String) extends Value(TString())
  }

  object TypeChecker {

    sealed abstract class TypeCheckerError(message:                    String) extends Exception(message)
    final case class NullComparisonWithNonNullableField(fieldSelector: FieldSelector)
        extends TypeCheckerError(s"Comparing non nullable field: ${fieldSelector} with NULL")
    final case class TypeMismatch(selector: FieldSelector, value: Value)
        extends TypeCheckerError(s"Type mismatch between field: ${selector} and value: ${value}")
    final case class InvalidOperatorForType(op: BinOpType, field: FieldSelector)
        extends TypeCheckerError(s"Invalid operand type for operator: ${op}, field: ${field}")

    def check(condition: Condition): Either[TypeCheckerError, Unit] = {
      condition match {
        // Check nullability of fields
        case UnOp(IsNull(), selector) if !selector.nullable =>
          Left(NullComparisonWithNonNullableField(selector))
        case UnOp(IsNotNull(), selector) if !selector.nullable =>
          Left(NullComparisonWithNonNullableField(selector))
        case UnOp(IsNull(), _) | UnOp(IsNotNull(), _) => Right(())
        // Check operator types and check that the operator is valid for the field type
        case BinOp(op, field, value) =>
          if (field.ty != value.ty) {
            Left(TypeMismatch(field, value))
          } else {

            // Eq and NotEq are generic
            (op, field.ty) match {
              case (Lt(), TString()) | (Gt(), TString()) | (Lte(), TString()) | (Gte(), TString()) =>
                Left(InvalidOperatorForType(op, field))
              case (Like(), TInt()) =>
                Left(InvalidOperatorForType(op, field))
              case _ => Right(())
            }
          }
        case And(left, right) => check(left).flatMap(_ => check(right))
        case Or(left, right)  => check(left).flatMap(_ => check(right))
      }
    }

  }

  object Compiler {

    final case class CompilerState(needsJoinedFields: Boolean)

    private val joinedFields = Set[FieldSelector](
      FieldSelector.Post.Id(),
      FieldSelector.Post.Title(),
      FieldSelector.Post.Content(),
      FieldSelector.Post.ViewCount(),
    )

    def compile(condition: Condition): Fragment = {
      val (state, conditionFragment) = compileCondition(condition).run(CompilerState(needsJoinedFields = false)).value
      val fieldSelectors             = fr"select blog.id, blog.name, blog.slug from blog"
      val joinedPosts = if (state.needsJoinedFields) {
        fr"left join post on blog.id = post.blog_id"
      } else {
        Fragment.empty
      }

      fieldSelectors ++ joinedPosts ++ Fragment.const("where") ++ conditionFragment
    }

    def compileCondition(condition: Condition): State[CompilerState, Fragment] = {
      condition match {
        case BinOp(op, field, value) =>
          for {
            _ <- if (joinedFields.contains(field)) {
              State.modify[CompilerState](_.copy(needsJoinedFields = true))
            } else {
              State.pure[CompilerState, Unit](())
            }
          } yield compileBinOp(op, field, value)
        case UnOp(op, field) =>
          for {
            _ <- if (joinedFields.contains(field)) {
              State.modify[CompilerState](_.copy(needsJoinedFields = true))
            } else {
              State.pure[CompilerState, Unit](())
            }
          } yield compileUnOp(op, field)
        case And(left, right) =>
          for {
            leftFragment <- compileCondition(left)
            rightFragment <- compileCondition(right)
          } yield Fragments.and(leftFragment, rightFragment)
        case Or(left, right) =>
          for {
            leftFragment <- compileCondition(left)
            rightFragment <- compileCondition(right)
          } yield Fragments.or(leftFragment, rightFragment)
      }
    }

    def compileBinOp(op: BinOpType, field: FieldSelector, value: Value): Fragment = {
      val opFragment = op match {
        case Eq()    => Fragment.const("=")
        case NotEq() => Fragment.const("!=")
        case Lt()    => Fragment.const("<")
        case Gt()    => Fragment.const(">")
        case Lte()   => Fragment.const("<=")
        case Gte()   => Fragment.const(">=")
        case Like()  => Fragment.const("LIKE")
      }

      renderField(field) ++ opFragment ++ renderValue(value)
    }

    def compileUnOp(op: UnOpType, field: FieldSelector): Fragment = {
      val opFragment = op match {
        case IsNull()    => Fragment.const("IS NULL")
        case IsNotNull() => Fragment.const("IS NOT NULL")
      }

      renderField(field) ++ opFragment
    }

    def renderField(field: FieldSelector): Fragment = {
      val (model, fieldName) = field match {
        case FieldSelector.Blog.Id()        => ("blog", "id")
        case FieldSelector.Blog.Name()      => ("blog", "name")
        case FieldSelector.Blog.Slug()      => ("blog", "slug")
        case FieldSelector.Post.Id()        => ("post", "id")
        case FieldSelector.Post.Title()     => ("post", "title")
        case FieldSelector.Post.Content()   => ("post", "content")
        case FieldSelector.Post.ViewCount() => ("post", "view_count")

      }
      Fragment.const(s"${model}.${fieldName}")
    }

    def renderValue(value: Value): Fragment = {
      value match {
        case Value.Number(number) => fr"${number}"
        case Value.Text(text)     => fr"${text}"
      }
    }
  }

  final case class IncompatibleQueryError() extends Exception("HasPosts query is not supported by Query2")

  def fromQuery(query: model.Query): Either[IncompatibleQueryError, Condition] = {
    query match {
      case model.Query.ByBlogId(id)       => Right(BinOp(Eq(), FieldSelector.Blog.Id(), Value.Text(id.value.toString)))
      case model.Query.ByBlogName(name)   => Right(BinOp(Like(), FieldSelector.Blog.Name(), Value.Text(name.value)))
      case model.Query.ByBlogSlug(slug)   => Right(BinOp(Eq(), FieldSelector.Blog.Slug(), Value.Text(slug.value)))
      case model.Query.HasPosts()         => Left(new IncompatibleQueryError())
      case model.Query.ByPostTitle(title) => Right(BinOp(Like(), FieldSelector.Post.Title(), Value.Text(title.value)))
      case model.Query.ByPostContent(content) =>
        Right(BinOp(Like(), FieldSelector.Post.Content(), Value.Text(content.value)))
    }
  }

}
