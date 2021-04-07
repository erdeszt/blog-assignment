package assignment.model

import doobie._
import doobie.syntax.string._

// TODO: Better error handling
// TODO: Type checking
// TODO: `HAVING` for `post.view_count`
// TODO: Explore idea: introduce an intermediate representation ("minisql") and compile the query
//       to it before rendering to eliminate the null comparison issue
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

  sealed abstract class FieldSelector(val nullable: Boolean) {
    type ValueType <: Value
  }
  object FieldSelector {
    object Blog {
      final case class Id() extends FieldSelector(nullable   = false) { type ValueType = Value.Text }
      final case class Name() extends FieldSelector(nullable = false) { type ValueType = Value.Text }
      final case class Slug() extends FieldSelector(nullable = false) { type ValueType = Value.Text }
    }
    object Post {
      final case class Id() extends FieldSelector(nullable        = false) { type ValueType = Value.Text }
      final case class Title() extends FieldSelector(nullable     = true) { type ValueType  = Value.Text }
      final case class Content() extends FieldSelector(nullable   = false) { type ValueType = Value.Text }
      final case class ViewCount() extends FieldSelector(nullable = false) { type ValueType = Value.Number }
    }
  }

  sealed trait Value
  object Value {
    final case class Number(value: Long) extends Value
    final case class Text(value:   String) extends Value
  }

  object TypeChecker {

    sealed abstract class TypeCheckerError(message:                    String) extends Exception(message)
    final case class NullComparisonWithNonNullableField(fieldSelector: FieldSelector)
        extends TypeCheckerError(s"Comparing non nullable field ${fieldSelector} with NULL")

    def check(condition: Condition): Unit = {
      condition match {
        case UnOp(IsNull(), selector) if !selector.nullable =>
          throw NullComparisonWithNonNullableField(selector)
        case UnOp(IsNotNull(), selector) if !selector.nullable =>
          throw NullComparisonWithNonNullableField(selector)
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

}
