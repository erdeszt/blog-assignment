package assignment.service

import assignment.RequestContext
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
  ): ZIO[RequestContext, Api.CreateBlogError, (Blog.Id, List[Post.Id])]
  def createPost(
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  ): ZIO[RequestContext, Api.CreatePostError, Post.Id]
  def queryBlogs(query: Query, includePosts: Boolean): UIO[List[Blog]]
}

object Api {

  type PostValidationError = OneOf2[EmptyPostTitle, EmptyPostContent]
  type CreateBlogError =
    OneOf6[EmptyBlogName, EmptyBlogSlug, InvalidBlogSlug, BlogSlugAlreadyExists, EmptyPostTitle, EmptyPostContent]
  type CreatePostError = OneOf4[BlogNotFound, EmptyPostTitle, EmptyPostContent, Unauthorized]

  private val blogSlugFormat = "^[a-zA-Z][a-zA-Z0-9\\-]*$".r

  final case class Live(idProvider: IdProvider, blogStore: BlogStore, postStore: PostStore, trx: TransactionHandler)
      extends Api {

    override def createBlog(
        name:  Blog.Name,
        slug:  Blog.Slug,
        posts: List[(Option[Post.Title], Post.Content)],
    ): ZIO[RequestContext, CreateBlogError, (Blog.Id, List[Post.Id])] = {
      implicit val errorEmbedder = Embedder.make[CreateBlogError]
      for {
        _ <- ZIO.when(name.value.isEmpty)(ZIO.fail(EmptyBlogName().embed))
        _ <- ZIO.when(slug.value.isEmpty)(ZIO.fail(EmptyBlogSlug().embed))
        _ <- ZIO.unless(blogSlugFormat.matches(slug.value))(ZIO.fail(InvalidBlogSlug(slug).embed))
        _ <- ZIO.foreach_(posts) { case (title, content) => validatePost[CreateBlogError](title, content) }
        _ <- ZIO.whenM(blogStore.queryBlogs(Query.ByBlogSlug(slug)).map(_.nonEmpty))(
          ZIO.fail(BlogSlugAlreadyExists(slug).embed),
        )
        ownerId <- ZIO.service[User]
        blogId <- idProvider.generateId.map(Blog.Id)
        blogPosts <- ZIO.foreach(posts) {
          case (title, content) =>
            idProvider.generateId.map(Post.Id).map(PostStore.Create(_, blogId, title, content))
        }
        _ <- trx.run {
          for {
            _ <- blogStore.createBlog(blogId, ownerId.id, name, slug)
            _ <- postStore.createPosts(blogPosts)
          } yield ()
        }
      } yield (blogId, blogPosts.map(_.id))
    }

    override def createPost(
        blogId:  Blog.Id,
        title:   Option[Post.Title],
        content: Post.Content,
    ): ZIO[RequestContext, CreatePostError, Post.Id] = {
      implicit val errorEmbedder = Embedder.make[CreatePostError]
      for {
        _ <- validatePost[CreatePostError](title, content)
        blog <- blogStore.getById(blogId).someOrFail(BlogNotFound(blogId).embed)
        user <- ZIO.service[User]
        _ <- ZIO.unless(blog.ownerId == user.id)(ZIO.fail(Unauthorized().embed))
        id <- idProvider.generateId.map(Post.Id)
        _ <- trx.run(postStore.createPost(PostStore.Create(id, blogId, title, content)))
      } yield id
    }

    override def queryBlogs(query: Query, includePosts: Boolean): UIO[List[Blog]] = {
      for {
        blogs <- blogStore.queryBlogs(query)
        posts <- if (includePosts) {
          postStore
            .getPostsByBlogIds(blogs.map(_.id))
            .map(_.groupBy(_.blogId))
        } else {
          UIO(Map.empty[Blog.Id, List[Post]])
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
  ): ZIO[Has[Api] with RequestContext, CreateBlogError, (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, slug, posts))

  def createPost(
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  ): ZIO[Has[Api] with RequestContext, Api.CreatePostError, Post.Id] = {
    ZIO.accessM(_.get.createPost(blogId, title, content))
  }

  def queryBlogs(query: Query, includePosts: Boolean): URIO[Has[Api], List[Blog]] = {
    ZIO.accessM(_.get.queryBlogs(query, includePosts))
  }

}
