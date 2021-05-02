package assignment.service

import assignment.model._
import assignment.service.TransactionHandler._trx
import doobie.syntax.string._
import org.atnos.eff._
import org.atnos.eff.interpret._

object BlogStore extends UUIDDatabaseMapping {

  sealed trait Op[+A]
  final case class GetById(id: Blog.Id) extends Op[Option[BlogStore.BlogRead]]

  type _blogStore[R] = Op |= R

  def getById[R: _blogStore](id: Blog.Id): Eff[R, Option[BlogRead]] = {
    Eff.send[Op, R, Option[BlogRead]](GetById(id))
  }

  final case class BlogRead(
      id:   Blog.Id,
      name: Blog.Name,
      slug: Blog.Slug,
  )

  def evalBlogStore[R, U, A](effect: Eff[R, A])(implicit m: Member.Aux[Op, R, U], trx: _trx[U]): Eff[U, A] = {
    translate(effect)(new Translate[Op, U] {
      override def apply[X](op: Op[X]): Eff[U, X] = {
        op match {
          case GetById(id) =>
            TransactionHandler
              .run {
                sql"select id, name, slug from blog where id = ${id}"
                  .query[BlogRead]
                  .option
                  .map(identity) // NOTE: Without .map(identity) it doesn't type check, intellij still shits the bed

              }
        }
      }
    })
  }

  implicit class BlogStoreEvaluator[R, U, A](effect: Eff[R, A])(implicit member: Member.Aux[Op, R, U], trx: _trx[U]) {
    def runBlogStore: Eff[U, A] = evalBlogStore(effect)
  }

}
