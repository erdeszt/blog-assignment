package assignment.service

import assignment.model.DomainError._
import assignment.model._
import hotpotato._
import shapeless.Coproduct
import shapeless.ops.coproduct.Basis
import zio._

trait Api {
  def createBlog(
      name:  Blog.Name,
      slug:  Blog.Slug,
      posts: List[(Option[Post.Title], Post.Content)],
  ): IO[Api.CreateBlogError, (Blog.Id, List[Post.Id])]
  def createPost(blogId:  Blog.Id, title: Option[Post.Title], content: Post.Content): IO[Api.CreatePostError, Post.Id]
  def getBlogs(withPosts: WithPosts): UIO[List[Blog]]
  def getBlogById(blogId: Blog.Id, withPosts: WithPosts): IO[Api.GetBlogByIdError, Blog]
  def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): IO[Api.GetBlogBySlugError, Blog]
}

object Api {

  type PostValidationError = OneOf2[EmptyPostTitle, EmptyPostContent]
  type CreateBlogError =
    OneOf6[EmptyBlogName, EmptyBlogSlug, InvalidBlogSlug, BlogSlugAlreadyExists, EmptyPostTitle, EmptyPostContent]
  type CreatePostError    = OneOf3[BlogNotFound, EmptyPostTitle, EmptyPostContent]
  type GetBlogByIdError   = BlogNotFound
  type GetBlogBySlugError = BlogSlugNotFound

  private val blogSlugFormat = "^[a-zA-Z][a-zA-Z0-9\\-]*$".r

  final case class Live(idProvider: IdProvider, blogStore: BlogStore, postStore: PostStore, trx: TransactionHandler)
      extends Api {

    override def createBlog(
        name:  Blog.Name,
        slug:  Blog.Slug,
        posts: List[(Option[Post.Title], Post.Content)],
    ): IO[CreateBlogError, (Blog.Id, List[Post.Id])] = {
      implicit val errorEmbedder = Embedder.make[CreateBlogError]
      for {
        _ <- ZIO.when(name.value.isEmpty)(ZIO.fail(EmptyBlogName().embed))
        _ <- ZIO.when(slug.value.isEmpty)(ZIO.fail(EmptyBlogSlug().embed))
        _ <- ZIO.unless(blogSlugFormat.matches(slug.value))(ZIO.fail(InvalidBlogSlug(slug).embed))
        _ <- ZIO.foreach_(posts) { case (title, content) => validatePost[CreateBlogError](title, content) }
        _ <- ZIO.whenM(blogStore.getBySlug(slug).map(_.nonEmpty))(
          ZIO.fail(BlogSlugAlreadyExists(slug).embed),
        )
        blogId <- idProvider.generateId.map(Blog.Id)
        blogPosts <- ZIO.foreach(posts) {
          case (title, content) =>
            idProvider.generateId.map(Post.Id).map(PostStore.Create(_, blogId, title, content))
        }
        _ <- trx.run {
          for {
            _ <- blogStore.createBlog(blogId, name, slug)
            _ <- postStore.createPosts(blogPosts)
          } yield ()
        }
      } yield (blogId, blogPosts.map(_.id))
    }

    override def createPost(
        blogId:  Blog.Id,
        title:   Option[Post.Title],
        content: Post.Content,
    ): IO[CreatePostError, Post.Id] = {
      implicit val errorEmbedder = Embedder.make[CreatePostError]
      for {
        _ <- validatePost[CreatePostError](title, content)
        _ <- blogStore.getById(blogId).someOrFail(BlogNotFound(blogId).embed)
        id <- idProvider.generateId.map(Post.Id)
        _ <- trx.run(postStore.createPost(PostStore.Create(id, blogId, title, content)))
      } yield id
    }

    override def getBlogs(withPosts: WithPosts): UIO[List[Blog]] = {
      for {
        blogs <- blogStore.getAll
        posts <- if (withPosts == WithPosts.Yes) {
          postStore.getPostsByBlogIds(blogs.map(_.id))
        } else {
          ZIO.succeed(Map.empty[Blog.Id, List[Post]])
        }
      } yield blogs.map { blog =>
        Blog(
          blog.id,
          blog.name,
          blog.slug,
          posts.getOrElse(blog.id, List.empty),
        )
      }
    }

    override def getBlogById(blogId: Blog.Id, withPosts: WithPosts): IO[GetBlogByIdError, Blog] = {
      for {
        blog <- blogStore.getById(blogId).someOrFail(BlogNotFound(blogId))
        posts <- if (withPosts == WithPosts.Yes) postStore.getPostsByBlogId(blogId) else ZIO.succeed(List.empty)
      } yield Blog(
        blog.id,
        blog.name,
        blog.slug,
        posts,
      )
    }

    override def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): IO[Api.GetBlogBySlugError, Blog] = {
      for {
        blog <- blogStore.getBySlug(slug).someOrFail(BlogSlugNotFound(slug))
        posts <- if (withPosts == WithPosts.Yes) postStore.getPostsByBlogId(blog.id) else ZIO.succeed(List.empty)
      } yield Blog(
        blog.id,
        blog.name,
        blog.slug,
        posts,
      )
    }

    def validatePost[E <: Coproduct](
        title:        Option[Post.Title],
        content:      Post.Content,
    )(implicit basis: Basis[E, PostValidationError]): IO[E, Unit] = {
      implicit val embedder = Embedder.make[PostValidationError]
      for {
        _ <- ZIO.when(title.exists(_.value.isEmpty))(ZIO.fail(basis.inverse(Right(EmptyPostTitle().embed))))
        _ <- ZIO.when(content.value.isEmpty)(ZIO.fail(basis.inverse(Right(EmptyPostContent().embed))))
      } yield ()
    }

  }

  val layer: URLayer[Has[IdProvider] with Has[BlogStore] with Has[PostStore] with Has[TransactionHandler], Has[Api]] =
    ZLayer.fromServices[IdProvider, BlogStore, PostStore, TransactionHandler, Api](Live)

  def createBlog(
      name:  Blog.Name,
      slug:  Blog.Slug,
      posts: List[(Option[Post.Title], Post.Content)],
  ): ZIO[Has[Api], CreateBlogError, (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, slug, posts))

  def createPost(
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  ): ZIO[Has[Api], Api.CreatePostError, Post.Id] = {
    ZIO.accessM(_.get.createPost(blogId, title, content))
  }

  def getBlogs(withPosts: WithPosts): ZIO[Has[Api], Nothing, List[Blog]] = {
    ZIO.accessM(_.get.getBlogs(withPosts))
  }

  def getBlogById(blogId: Blog.Id, withPosts: WithPosts): ZIO[Has[Api], Api.GetBlogByIdError, Blog] = {
    ZIO.accessM(_.get.getBlogById(blogId, withPosts))
  }

  def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): ZIO[Has[Api], Api.GetBlogBySlugError, Blog] = {
    ZIO.accessM(_.get.getBlogBySlug(slug, withPosts))
  }

}
