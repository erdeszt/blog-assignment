package assignment

import assignment.model._
import zio._

/**
 * TODO:
 *    - Setup stores
 *    - Domain errors
 */
type Api = Has[Api.Service]

object Api:

  trait Service:
    def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[Blog.Id]
    def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id]
    def queryBlogs(query: Query): UIO[List[Blog]]
    
  class Live(idProvider: IdProvider.Service) extends Api.Service:
    override def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): UIO[Blog.Id] =
      idProvider.generateId.map(Blog.Id(_))
      
    override def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): UIO[Post.Id] = ZIO.die(new Exception("Stop"))
    override def queryBlogs(query: Query): UIO[List[Blog]] = ZIO.die(new Exception("Stop"))
    
  val layer: URLayer[IdProvider, Api] =
    ZLayer.fromService(Live(_))
    
  def createBlog(name: Blog.Name, posts: List[(Option[Post.Title], Post.Body)]): URIO[Api, Blog.Id] =
    ZIO.accessM(_.get.createBlog(name, posts))
    
  def createPost(blogId: Blog.Id, title: Post.Title, body: Post.Body): URIO[Api, Post.Id] =
    ZIO.accessM(_.get.createPost(blogId, title, body))

  def queryBlogs(query: Query): URIO[Api, List[Blog]] =
    ZIO.accessM(_.get.queryBlogs(query))

