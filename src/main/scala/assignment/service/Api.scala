package assignment.service

import assignment.model.DomainError._
import assignment.model._
import cats.data.OptionT
import hotpotato._
import zio._
import zio.interop.catz._

/**
  * TODO:
  *    - Setup stores
  *    - Domain errors
  *    - Consider moving id provider to store
  */
trait Api {
  def createBlog(
      name:  Blog.Name,
      posts: List[(Option[Post.Title], Post.Body)]
  ): IO[Api.CreateBlogError, (Blog.Id, List[Post.Id])]
  def createPost(blogId: Blog.Id, title:      Option[Post.Title], body: Post.Body): IO[Api.CreatePostError, Post.Id]
  def queryBlogs(query:  Query, includePosts: Boolean): UIO[List[Blog]]
}

object Api {

  type CreateBlogError = OneOf2[EmptyBlogName, EmptyPostBody]
  type CreatePostError = OneOf2[BlogNotFound, EmptyPostBody]

  final case class Live(idProvider: IdProvider, blogStore: BlogStore, postStore: PostStore, trx: TransactionHandler)
      extends Api {

    override def createBlog(
        name:  Blog.Name,
        posts: List[(Option[Post.Title], Post.Body)]
    ): IO[CreateBlogError, (Blog.Id, List[Post.Id])] = {
      implicit val errorEmbedder = Embedder.make[CreateBlogError]
      for {
        _ <- ZIO.when(name.value.isEmpty)(ZIO.fail(EmptyBlogName().embed))
        _ <- ZIO.when(posts.exists { case (_, body) => body.value.isEmpty })(ZIO.fail(EmptyPostBody().embed))
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

    override def createPost(
        blogId: Blog.Id,
        title:  Option[Post.Title],
        body:   Post.Body
    ): IO[CreatePostError, Post.Id] = {
      implicit val errorEmbedder = Embedder.make[CreatePostError]
      for {
        _ <- ZIO.when(body.value.isEmpty)(ZIO.fail(EmptyPostBody().embed))
        _ <- blogStore.getById(blogId).someOrFail(BlogNotFound(blogId).embed)
        id <- idProvider.generateId.map(Post.Id)
        _ <- trx.run(postStore.createPost(PostStore.Create(id, blogId, title, body)))
      } yield id
    }

    override def queryBlogs(query: Query, includePosts: Boolean): UIO[List[Blog]] = {
      query match {
        case Query.ByBlogId(id) =>
          val blogWithPosts = for {
            blog <- OptionT(blogStore.getById(id))
            posts <- if (includePosts) {
              OptionT.liftF(postStore.getPostsByBlogId(id))
            } else {
              OptionT.pure[UIO](List.empty)
            }
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
  ): ZIO[Has[Api], CreateBlogError, (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, posts))

  def createPost(
      blogId: Blog.Id,
      title:  Option[Post.Title],
      body:   Post.Body
  ): ZIO[Has[Api], Api.CreatePostError, Post.Id] = {
    ZIO.accessM(_.get.createPost(blogId, title, body))
  }

  def queryBlogs(query: Query, includePosts: Boolean): URIO[Has[Api], List[Blog]] = {
    ZIO.accessM(_.get.queryBlogs(query, includePosts))
  }

}
