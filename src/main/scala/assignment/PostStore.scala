package assignment

import assignment.model._
import doobie._
import zio._

trait PostStore {
  def createPost(id: Post.Id, blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): UIO[Unit]
}

object PostStore {
  class Live(transactor: Transactor[Task]) extends PostStore {
    override def createPost(id: Post.Id, blogId: Blog.Id, title: Option[Post.Title], body: Post.Body): UIO[Unit] = {
      ???
    }
  }
}