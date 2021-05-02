package assignment.service

import assignment.model._
import cats.effect.IO
import doobie._
import doobie.syntax.string._
import doobie.syntax.connectionio._
import org.atnos.eff._
import org.atnos.eff.addon.cats.effect.IOEffect._
import org.atnos.eff.interpret._

sealed trait BlogStoreOp[+A]
final case class GetById(id: Blog.Id) extends BlogStoreOp[Option[BlogStore.BlogRead]]

object BlogStore extends UUIDDatabaseMapping {

  type _blogStore[R] = BlogStoreOp |= R

  def getById[R: _blogStore](id: Blog.Id): Eff[R, Option[BlogRead]] = {
    Eff.send[BlogStoreOp, R, Option[BlogRead]](GetById(id))
  }

  final case class BlogRead(
      id:   Blog.Id,
      name: Blog.Name,
      slug: Blog.Slug,
  )

  def evalBlogStore[R, U, A](
      trx:  Transactor[IO],
  )(effect: Eff[R, A])(implicit m: Member.Aux[BlogStoreOp, R, U], io: _io[U]): Eff[U, A] = {
    translate(effect)(new Translate[BlogStoreOp, U] {
      override def apply[X](op: BlogStoreOp[X]): Eff[U, X] = {
        op match {
          case GetById(id) =>
            fromIO {
              sql"select id, name, slug from blog where id = ${id}"
                .query[BlogRead]
                .option
                .transact(trx)
            }
        }
      }
    })
  }

}
