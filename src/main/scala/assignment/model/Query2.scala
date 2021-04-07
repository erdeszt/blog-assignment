package assignment.model

import assignment.model
import doobie._
import doobie.syntax.string._

// TODO: Better error handling
// TODO: Type checking
// TODO: `HAVING` for `post.view_count`
// TODO: Explore idea: separate representations for transfer & rendering
//  (could have `x = null` in the frontend translated to `is not null(x)` in the intermediate representation
object Query2 {

  /*

   Default JSON representation

  { "Comparison": { "op": "eq", "field": "blog.id", "value": "blog1" }}

  { "And": { "left": { "Comparison": { "op": "eq", "field": "blog.id", "value": "blog1" }} },
           { "right": { "Comparison": { "op": "gt", "field": "post.view_count", "value": 0 }} } }

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

    def check(condition: Condition): Unit = {
      condition match {
        // Check nullability of fields
        case UnOp(IsNull(), selector) if !selector.nullable =>
          throw NullComparisonWithNonNullableField(selector)
        case UnOp(IsNotNull(), selector) if !selector.nullable =>
          throw NullComparisonWithNonNullableField(selector)
        case UnOp(IsNull(), _) | UnOp(IsNotNull(), _) => ()
        // Check operator types and check that the operator is valid for the field type
        case BinOp(op, field, value) =>
          if (field.ty != value.ty) {
            throw TypeMismatch(field, value)
          }

          // Eq and NotEq are generic
          (op, field.ty) match {
            case (Lt(), TString()) | (Gt(), TString()) | (Lte(), TString()) | (Gte(), TString()) =>
              throw InvalidOperatorForType(op, field)
            case (Like(), TInt()) =>
              throw InvalidOperatorForType(op, field)
            case _ => ()
          }
        case And(left, right) => {
          check(left)
          check(right)
        }
        case Or(left, right) => {
          check(left)
          check(right)
        }
      }
    }

  }

  object Compiler {

    val joinedFields = Set[FieldSelector](
      FieldSelector.Post.Id(),
      FieldSelector.Post.Title(),
      FieldSelector.Post.Content(),
      FieldSelector.Post.ViewCount(),
    )

    def getFields(condition: Condition): Set[FieldSelector] = {
      def go(fields: Set[FieldSelector]): Condition => Set[FieldSelector] = {
        case BinOp(_, field, _) => fields + field
        case UnOp(_, field)     => fields + field
        case And(left, right)   => go(fields)(left).union(go(fields)(right))
        case Or(left, right)    => go(fields)(left).union(go(fields)(right))
      }
      go(Set.empty)(condition)
    }

    def needsJoinedFields(condition: Condition): Boolean = {
      val fields = getFields(condition)

      (joinedFields.intersect(fields)).nonEmpty
    }

    def compile(condition: Condition): Fragment = {
      condition match {
        case BinOp(op, field, value) => compileBinOp(op, field, value)
        case UnOp(op, field)         => compileUnOp(op, field)
        case And(left, right)        => Fragments.and(compile(left), compile(right))
        case Or(left, right)         => Fragments.or(compile(left), compile(right))
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

  def fromQuery(query: model.Query): Condition = {
    query match {
      case model.Query.ByBlogId(id)           => BinOp(Eq(), FieldSelector.Blog.Id(), Value.Text(id.value.toString))
      case model.Query.ByBlogName(name)       => BinOp(Like(), FieldSelector.Blog.Name(), Value.Text(name.value))
      case model.Query.ByBlogSlug(slug)       => BinOp(Eq(), FieldSelector.Blog.Slug(), Value.Text(slug.value))
      case model.Query.HasPosts()             => throw new Exception("Not supported")
      case model.Query.ByPostTitle(title)     => BinOp(Like(), FieldSelector.Post.Title(), Value.Text(title.value))
      case model.Query.ByPostContent(content) => BinOp(Like(), FieldSelector.Post.Content(), Value.Text(content.value))
    }
  }

}
