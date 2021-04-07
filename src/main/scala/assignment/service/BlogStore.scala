package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id:     Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
  def getById(id:        Blog.Id): UIO[Option[BlogStore.BlogRead]]
  def queryBlogs(query:  Query): UIO[List[BlogStore.BlogRead]]
  def queryBlogs2(query: Query2.Condition): UIO[List[BlogStore.BlogRead]]
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
      queryBlogs(Query.ByBlogId(id)).map(_.headOption)
    }

    override def queryBlogs(query: Query): UIO[List[BlogRead]] = {
      queryBlogs2(Query2.fromQuery(query)) // TODO: Unsafe
    }

    // TODO: Move the type checker to the service layer?
    override def queryBlogs2(query: Query2.Condition): UIO[List[BlogRead]] = {
      for {
        _ <- ZIO.fromEither(Query2.TypeChecker.check(query)).orDie
        sqlQuery = Query2.Compiler.compile(query)
        blogs <- trx.run(sqlQuery.query[BlogRead].to[List])
      } yield blogs
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
