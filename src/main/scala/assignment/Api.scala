package assignment

import assignment.model._
import zio._

/**
  * TODO:
  *    - Setup stores
  *    - Domain errors
  */
trait Api {
  def createBlog(name:   Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[(Blog.Id, List[Post.Id])]
  def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id]
  def queryBlogs(query:  Query): UIO[List[Blog]]
}

object Api {

  final case class Live(idProvider: IdProvider) extends Api {
    override def createBlog(
        name:  Blog.Name,
        posts: List[(Option[Post.Title], Post.Body)]
    ): UIO[(Blog.Id, List[Post.Id])] = {
      for {
        blogId <- idProvider.generateId.map(Blog.Id)
        postIds <- ZIO.replicateM(posts.length)(idProvider.generateId).map(_.map(Post.Id).toList)
      } yield (blogId, postIds)
    }

    override def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id] = {
      ZIO.die(new Exception("Stop"))
    }

    override def queryBlogs(query: Query): UIO[List[Blog]] = {
      UIO(List.empty)
    }
  }

  val layer: URLayer[Has[IdProvider], Has[Api]] =
    ZLayer.fromService(Live)

  def createBlog(
      name:  Blog.Name,
      posts: List[(Option[Post.Title], Post.Body)]
  ): URIO[Has[Api], (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, posts))

  def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): URIO[Has[Api], Post.Id] =
    ZIO.accessM(_.get.createPost(blogId, title, body))

  def queryBlogs(query: Query): URIO[Has[Api], List[Blog]] =
    ZIO.accessM(_.get.queryBlogs(query))

}
