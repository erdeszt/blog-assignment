package assignment.service

import assignment.model._
import assignment.model.Query2._
import cats.syntax.functor._
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id:    Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
  def getById(id:       Blog.Id): UIO[Option[BlogStore.BlogRead]]
  def getBySlug(slug:   Blog.Slug): UIO[Option[BlogStore.BlogRead]]
  def queryBlogs(query: Query2.Condition): UIO[List[BlogStore.BlogRead]]
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

    override def getById(id: Blog.Id): UIO[Option[BlogStore.BlogRead]] = {
      queryBlogs(BinOp(Eq(), FieldSelector.Blog.Id(), Value.Text(id.value.toString)))
        .map(_.headOption)
    }

    override def getBySlug(slug: Blog.Slug): UIO[Option[BlogStore.BlogRead]] = {
      queryBlogs(BinOp(Eq(), FieldSelector.Blog.Slug(), Value.Text(slug.value)))
        .map(_.headOption)
    }

    override def queryBlogs(query: Query2.Condition): UIO[List[BlogRead]] = {
      val sqlQuery = Query2.Compiler.compile(query)

      trx.run(sqlQuery.query[BlogRead].to[List])
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
