package assignment.service

import assignment.model._
import cats.data.OptionT
import zio._
import zio.interop.catz._

/**
  * TODO:
  *    - Setup stores
  *    - Domain errors
  *    - Consider moving id provider to store
  */
trait Api {
  def createBlog(name:   Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[(Blog.Id, List[Post.Id])]
  def createPost(blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): UIO[Post.Id]
  def queryBlogs(query:  Query): UIO[List[Blog]]
}

object Api {

  final case class Live(idProvider: IdProvider, blogStore: BlogStore, postStore: PostStore, trx: TransactionHandler)
      extends Api {

    override def createBlog(
        name:  Blog.Name,
        posts: List[(Option[Post.Title], Post.Body)]
    ): UIO[(Blog.Id, List[Post.Id])] = {
      for {
        blogId <- idProvider.generateId.map(Blog.Id)
        blogPosts <- ZIO.foreach(posts) {
          case (title, body) =>
            idProvider.generateId.map(Post.Id).map(PostStore.Create(_, blogId, title, body))
        }
        _ <- trx.run {
          for {
            _ <- blogStore.createBlog(blogId, name, Blog.Slug(name.value))
            _ <- postStore.createPosts(blogPosts)
          } yield ()
        }
      } yield (blogId, blogPosts.map(_.id))
    }

    override def createPost(blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): UIO[Post.Id] = {
      ZIO.die(new Exception("Stop"))
    }

    override def queryBlogs(query: Query): UIO[List[Blog]] = {
      query match {
        case Query.ByBlogId(id) =>
          val blogWithPosts = for {
            blog <- OptionT(blogStore.getById(id))
            posts <- OptionT.liftF(postStore.getPostsByBlogId(id))
          } yield Blog(
            blog.id,
            blog.name,
            blog.slug,
            posts
          )

          blogWithPosts.value.map(_.toList)
      }
    }

  }

  val layer: URLayer[Has[IdProvider] with Has[BlogStore] with Has[PostStore] with Has[TransactionHandler], Has[Api]] =
    ZLayer.fromServices[IdProvider, BlogStore, PostStore, TransactionHandler, Api](Live)

  def createBlog(
      name:  Blog.Name,
      posts: List[(Option[Post.Title], Post.Body)]
  ): URIO[Has[Api], (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, posts))

  def createPost(blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): URIO[Has[Api], Post.Id] = {
    ZIO.accessM(_.get.createPost(blogId, title, body))
  }

  def queryBlogs(query: Query): URIO[Has[Api], List[Blog]] = {
    ZIO.accessM(_.get.queryBlogs(query))
  }

}
