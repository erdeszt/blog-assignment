package assignment

import assignment.model._
import zio._

/**
 * TODO:
 *    - Setup stores
 *    - Domain errors
 */
trait Api:
  def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[Blog.Id]
  def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id]
  def queryBlogs(query: Query): UIO[List[Blog]]
    
object Api:
  class Live(idProvider: IdProvider) extends Api:
    override def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[Blog.Id] =
      idProvider.generateId.map(Blog.Id(_))
      
    override def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id] = ZIO.die(new Exception("Stop"))
    override def queryBlogs(query: Query): UIO[List[Blog]] = ZIO.die(new Exception("Stop"))
    
  val layer: URLayer[Has[IdProvider], Has[Api]] =
    ZLayer.fromService(Live(_))
    
  def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): URIO[Has[Api], Blog.Id] =
    ZIO.accessM(_.get.createBlog(name, posts))
    
  def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): URIO[Has[Api], Post.Id] =
    ZIO.accessM(_.get.createPost(blogId, title, body))

  def queryBlogs(query: Query): URIO[Has[Api], List[Blog]] =
    ZIO.accessM(_.get.queryBlogs(query))

