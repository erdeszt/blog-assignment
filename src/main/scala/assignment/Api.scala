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
