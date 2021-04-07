package assignment.model

import doobie._
import doobie.syntax.string._

object Query2 {

  sealed trait Condition
  final case class Comparison(op: Comparison.Op, field: FieldSelector, value: Value) extends Condition
  final case class And(left:      Condition, right:     Condition) extends Condition
  final case class Or(left:       Condition, right:     Condition) extends Condition

  object Comparison {
    sealed trait Op
    final case class Eq() extends Op
    final case class NotEq() extends Op
    final case class Lt() extends Op
    final case class Gt() extends Op
    final case class Lte() extends Op
    final case class Gte() extends Op
    final case class Like() extends Op
  }

  final case class FieldSelector(model: String, field: String)

  sealed trait Value
  object Value {
    final case class Null() extends Value
    final case class Number(value: Long) extends Value
    final case class Text(value:   String) extends Value
  }

  object Compiler {

    sealed abstract class CompileError(message: String) extends Exception(message)
    final case class InvalidNullComparison(op:  Comparison.Op)
        extends CompileError(s"Invalid Null comparison with operator: ${op}")

    def compile(condition: Condition): Fragment = {
      condition match {
        case Comparison(op, field, value) => compileOp(op, field, value)
        case And(left, right)             => Fragments.and(compile(left), compile(right))
        case Or(left, right)              => Fragments.or(compile(left), compile(right))
      }
    }

    // TODO: Better error handling
    def compileOp(op: Comparison.Op, field: FieldSelector, value: Value): Fragment = {
      (op, value) match {
        case (Comparison.Eq(), Value.Null())    => renderField(field) ++ Fragment.const("IS") ++ renderValue(value)
        case (Comparison.NotEq(), Value.Null()) => renderField(field) ++ Fragment.const("IS NOT") ++ renderValue(value)
        case (_, Value.Null())                  => throw InvalidNullComparison(op)
        case (Comparison.Eq(), _)               => renderField(field) ++ Fragment.const("=") ++ renderValue(value)
        case (Comparison.NotEq(), _)            => renderField(field) ++ Fragment.const("!=") ++ renderValue(value)
        case (Comparison.Lt(), _)               => renderField(field) ++ Fragment.const("<") ++ renderValue(value)
        case (Comparison.Gt(), _)               => renderField(field) ++ Fragment.const(">") ++ renderValue(value)
        case (Comparison.Lte(), _)              => renderField(field) ++ Fragment.const("<=") ++ renderValue(value)
        case (Comparison.Gte(), _)              => renderField(field) ++ Fragment.const(">=") ++ renderValue(value)
        case (Comparison.Like(), _)             => renderField(field) ++ Fragment.const("LIKE") ++ renderValue(value)
      }
    }

    def renderField(field: FieldSelector): Fragment = {
      Fragment.const(s"${field.model}.${field.field}")
    }

    def renderValue(value: Value): Fragment = {
      value match {
        case Value.Null()         => Fragment.const("NULL")
        case Value.Number(number) => fr"${number}"
        case Value.Text(text)     => fr"${text}"
      }
    }
  }

}
