package assignment.service

import assignment.model._
import cats.syntax.functor._
import doobie.syntax.string._
import doobie.util.update.Update
import zio._

trait PostStore {
  def createPost(post:         PostStore.Create):       Trx[Unit]
  def createPosts(posts:       List[PostStore.Create]): Trx[Unit]
  def getPostsByBlogId(blogId: Blog.Id):                UIO[List[Post]]
}

object PostStore extends UUIDDatabaseMapping {

  final case class Create(
      id:      Post.Id,
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  )

  final case class Live(trx: TransactionHandler) extends PostStore {

    override def createPost(post: Create): Trx[Unit] = {
      createPosts(List(post))
    }

    override def createPosts(posts: List[Create]): Trx[Unit] = {
      Update[Create]("insert into post (id, blog_id, title, content) value (?, ?, ?, ?)").updateMany(posts).void
    }

    override def getPostsByBlogId(blogId: Blog.Id): UIO[List[Post]] = {
      trx.run {
        sql"select id, blog_id, title, content, view_count from post where blog_id = ${blogId}"
          .query[Post]
          .to[List]
      }
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[PostStore]] =
    ZLayer.fromService(Live)

}
