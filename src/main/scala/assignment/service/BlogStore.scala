package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id: Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
}

object BlogStore extends UUIDDatabaseMapping {

  final case class BlogRead(
      id:   Blog.Id,
      name: Blog.Name,
      slug: Blog.Slug,
  )

  final case class Live(trx: TransactionHandler) extends BlogStore {

    override def createBlog(id: Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit] = {
      sql"insert into blog (id, name, slug) values (${id}, ${name}, ${slug})".update.run.void
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
