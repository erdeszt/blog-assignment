package assignment.service

import assignment.model._
import cats.data.NonEmptyList
import cats.syntax.functor._
import doobie.Fragments
import doobie.syntax.string._
import doobie.util.update.Update
import zio._

trait PostStore {
  def createPost(post:           PostStore.Create):       Trx[Unit]
  def createPosts(posts:         List[PostStore.Create]): Trx[Unit]
  def getPostsByBlogIds(blogIds: List[Blog.Id]):          UIO[List[Post]]
}

object PostStore extends DoobieUUIDUtils {

  final case class Create(
      id:     Post.Id,
      blogId: Blog.Id,
      title:  Option[Post.Title],
      body:   Post.Body
  )

  final case class Live(trx: TransactionHandler) extends PostStore {

    override def createPost(post: Create): Trx[Unit] = {
      createPosts(List(post))
    }

    override def createPosts(posts: List[Create]): Trx[Unit] = {
      Update[Create]("insert into post (id, blog_id, title, body) value (?, ?, ?, ?)").updateMany(posts).void
    }

    override def getPostsByBlogIds(blogIds: List[Blog.Id]): UIO[List[Post]] = {
      NonEmptyList.fromList(blogIds) match {
        case None => UIO(List.empty)
        case Some(ids) =>
          trx.run {
            (fr"select id, blog_id, title, body, view_count from post where " ++ Fragments.in(fr"blog_id", ids))
              .query[Post]
              .to[List]
          }
      }
    }

  }

  val layer: URLayer[Has[TransactionHandler], Has[PostStore]] =
    ZLayer.fromService(Live)

}
