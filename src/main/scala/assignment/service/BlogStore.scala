package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.Fragment
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id:    Blog.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
  def getById(id:       Blog.Id): UIO[Option[BlogStore.BlogRead]]
  def queryBlogs(query: Query): UIO[List[BlogStore.BlogRead]]
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
      val query2            = Query2.fromQuery(query)
      val selector          = fr"select blog.id, blog.name, blog.slug"
      val _                 = Query2.TypeChecker.check(query2)
      val condition         = Query2.Compiler.compile(query2)
      val needsJoinedFields = Query2.Compiler.needsJoinedFields(query2)
      val extraFields       = fr"post.id, post.title, post.content, post.view_count"
      val fullSelector = if (needsJoinedFields) {
        selector ++ Fragment.const(",") ++ extraFields ++ fr"from blog"
      } else {
        selector ++ fr"from blog"
      }
      val joinedFields = fr"left join post on blog.id = post.blog_id"

      val fullQuery = fullSelector ++ (if (needsJoinedFields) joinedFields else Fragment.empty) ++ fr"where" ++ condition

      trx.run(fullQuery.query[BlogRead].to[List])
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
