package assignment

import assignment.model._
import doobie._
import doobie.syntax.string._
import doobie.syntax.connectionio._
import java.util.UUID
import zio._
import zio.interop.catz._

trait BlogStore {
  def createBlog(id: Blog.Id, name: Blog.Name, slug: Blog.Slug): UIO[Unit]
}

object BlogStore {

  implicit val putUUID: Put[UUID] = Put[String].contramap(_.toString)

  class Live(transactor: Transactor[Task]) extends BlogStore {
    override def createBlog(id: Blog.Id, name: Blog.Name, slug: Blog.Slug): UIO[Unit] = {
      sql"insert into blog (id, name, slug) values (${id}, ${name}, ${slug})".update.run
        .transact(transactor)
        .unit
        .orDie
    }
  }

}
