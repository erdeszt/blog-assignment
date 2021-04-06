package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.syntax.string._
import zio._

trait BlogStore {
  def createBlog(id:    Blog.Id, owner: User.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit]
  def getById(id:       Blog.Id): UIO[Option[BlogStore.BlogRead]]
  def queryBlogs(query: Query): UIO[List[BlogStore.BlogRead]]
}

object BlogStore extends UUIDDatabaseMapping {

  final case class BlogRead(
      id:      Blog.Id,
      ownerId: User.Id,
      name:    Blog.Name,
      slug:    Blog.Slug,
  )

  final case class Live(trx: TransactionHandler) extends BlogStore {

    override def createBlog(id: Blog.Id, owner: User.Id, name: Blog.Name, slug: Blog.Slug): Trx[Unit] = {
      sql"insert into blog (id, owner_id, name, slug) values (${id}, ${owner}, ${name}, ${slug})".update.run.void
    }

    override def getById(id: Blog.Id): UIO[Option[BlogStore.BlogRead]] = {
      queryBlogs(Query.ByBlogId(id)).map(_.headOption)
    }

    override def queryBlogs(query: Query): UIO[List[BlogRead]] = {
      val selector = fr"select blog.id, blog.owner_id, blog.name, blog.slug from blog "
      val fullQuery = query match {
        case Query.ByBlogId(id) =>
          selector ++ fr"where blog.id = ${id}"
        case Query.ByBlogSlug(slug) =>
          selector ++ fr"where blog.slug = ${slug}"
        case Query.ByBlogName(name) =>
          selector ++ fr"where blog.name like ${name}"
        case Query.HasPosts() =>
          selector ++ fr"left join post on blog.id = post.blog_id having count(post.id) > 0"
        case Query.ByPostTitle(title) =>
          selector ++ fr"join post on blog.id = post.blog_id and post.title like ${title}"
        case Query.ByPostContent(content) =>
          selector ++ fr"join post on blog.id = post.blog_id and post.content like ${content}"
      }

      trx.run(fullQuery.query[BlogRead].to[List])
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[BlogStore]] =
    ZLayer.fromService(Live)

}
