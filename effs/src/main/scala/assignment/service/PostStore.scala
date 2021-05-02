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

sealed trait PostStoreOp[+A]
final case class CreatePost(post: PostStore.Create) extends PostStoreOp[Unit]

object PostStore extends UUIDDatabaseMapping {

  // TODO: TRX ?R/W

  type _postStore[R] = PostStoreOp |= R

  def createPost[R: _postStore](post: Create): Eff[R, Unit] = {
    Eff.send[PostStoreOp, R, Unit](CreatePost(post))
  }

  final case class Create(
      id:      Post.Id,
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  )

  def evalPostStore[R, U, A](
      trx:  Transactor[IO],
  )(effect: Eff[R, A])(implicit m: Member.Aux[PostStoreOp, R, U], io: _io[U]): Eff[U, A] = {
    translate(effect)(new Translate[PostStoreOp, U] {
      override def apply[X](op: PostStoreOp[X]): Eff[U, X] = {
        op match {
          case CreatePost(post) =>
            fromIO {
              sql"insert into post (id, blog_id, title, content) value (${post.id}, ${post.blogId}, ${post.title}, ${post.content})".update.run
                .transact(trx)
                .void
            }
        }
      }
    })
  }

}
