package assignment.service

import assignment.model._
import cats.effect.IO
import cats.syntax.functor._
import doobie._
import doobie.syntax.string._
import doobie.syntax.connectionio._
import doobie.util.update.Update
import org.atnos.eff._
import org.atnos.eff.addon.cats.effect.IOEffect._
import org.atnos.eff.interpret._

object PostStore extends UUIDDatabaseMapping {

  sealed trait Op[+A]
  final case class CreatePost(post: PostStore.Create) extends Op[Trx[Unit]]

  // TODO: TRX ?R/W

  type _postStore[R] = Op |= R

  def createPost[R: _postStore](post: Create): Eff[R, Trx[Unit]] = {
    Eff.send[Op, R, Trx[Unit]](CreatePost(post))
  }

  final case class Create(
      id:      Post.Id,
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  )

  def evalPostStore[R, U, A](effect: Eff[R, A])(implicit m: Member.Aux[Op, R, U]): Eff[U, A] = {
    translate(effect)(new Translate[Op, U] {
      override def apply[X](op: Op[X]): Eff[U, X] = {
        op match {
          case CreatePost(post) =>
            Eff.pure(
              sql"insert into post (id, blog_id, title, content) value (${post.id}, ${post.blogId}, ${post.title}, ${post.content})".update.run.void,
            )
        }
      }
    })
  }

  implicit class PostStoreEvaluator[R, U, A](effect: Eff[R, A])(implicit m: Member.Aux[Op, R, U]) {
    def runPostStore: Eff[U, A] = evalPostStore(effect)
  }

}
