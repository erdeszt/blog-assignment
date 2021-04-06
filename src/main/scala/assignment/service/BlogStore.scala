package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id:  Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
  def getById(id:     Blog.Id): UIO[Option[BlogStore.BlogRead]]
  def getBySlug(slug: Blog.Slug): UIO[Option[BlogStore.BlogRead]]
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

    override def getById(id: Blog.Id): UIO[Option[BlogRead]] = {
      trx.run {
        sql"select id, name, slug from blog where id = ${id} order by created_at desc".query[BlogRead].option
      }
    }

    override def getBySlug(slug: Blog.Slug): UIO[Option[BlogStore.BlogRead]] = {
      trx.run {
        sql"select id, name, slug from blog where slug = ${slug} order by created_at desc".query[BlogRead].option
      }
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
